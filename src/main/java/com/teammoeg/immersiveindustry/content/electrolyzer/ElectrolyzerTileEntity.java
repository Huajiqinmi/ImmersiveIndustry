/*
 * Copyright (c) 2021 TeamMoeg
 *
 * This file is part of Immersive Industry.
 *
 * Immersive Industry is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Immersive Industry is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Immersive Industry. If not, see <https://www.gnu.org/licenses/>.
 */

package com.teammoeg.immersiveindustry.content.electrolyzer;

import blusunrize.immersiveengineering.api.IEEnums;
import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.energy.immersiveflux.FluxStorage;
import blusunrize.immersiveengineering.api.energy.immersiveflux.FluxStorageAdvanced;
import blusunrize.immersiveengineering.common.blocks.IEBaseTileEntity;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces;
import blusunrize.immersiveengineering.common.util.EnergyHelper;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.inventory.IEInventoryHandler;
import blusunrize.immersiveengineering.common.util.inventory.IIEInventory;
import com.teammoeg.immersiveindustry.IIConfig;
import com.teammoeg.immersiveindustry.IIContent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.Property;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ElectrolyzerTileEntity extends IEBaseTileEntity implements IIEInventory, EnergyHelper.IIEInternalFluxHandler,
        ITickableTileEntity, IEBlockInterfaces.IProcessTile, IEBlockInterfaces.IStateBasedDirectional, IEBlockInterfaces.IInteractionObjectIE {
    public int process = 0;
    public int processMax = 0;
    public final int energyConsume;
    public FluxStorageAdvanced energyStorage = new FluxStorageAdvanced(20000);
    public FluidTank tank = new FluidTank(8 * FluidAttributes.BUCKET_VOLUME, ElectrolyzerRecipe::isValidRecipeFluid);
    private NonNullList<ItemStack> inventory = NonNullList.withSize(2, ItemStack.EMPTY);

    public ElectrolyzerTileEntity() {
        super(IIContent.IITileTypes.ELECTROLYZER.get());
        energyConsume = IIConfig.COMMON.electrolyzerConsume.get();
    }

    @Override
    public void readCustomNBT(CompoundNBT nbt, boolean descPacket) {
        energyStorage.readFromNBT(nbt);
        tank.readFromNBT(nbt.getCompound("tank"));
        process = nbt.getInt("process");
        processMax = nbt.getInt("processMax");
        ItemStackHelper.loadAllItems(nbt, inventory);
    }

    @Override
    public void writeCustomNBT(CompoundNBT nbt, boolean descPacket) {
        energyStorage.writeToNBT(nbt);
        nbt.put("tank", tank.writeToNBT(new CompoundNBT()));
        nbt.putInt("process", process);
        nbt.putInt("processMax", processMax);
        ItemStackHelper.saveAllItems(nbt, inventory);
    }

    @Override
    public void tick() {
        if (!world.isRemote) {
            if (energyStorage.getEnergyStored() >= energyConsume) {
                ElectrolyzerRecipe recipe = getRecipe();
                if (process > 0) {
                    if (inventory.get(0).isEmpty()) {
                        process = 0;
                        processMax = 0;
                    }
                    // during process
                    else {
                        if (recipe == null || recipe.time != processMax) {
                            process = 0;
                            processMax = 0;
                        } else {
                            process--;
                            energyStorage.extractEnergy(energyConsume, false);
                        }
                    }
                    this.markContainingBlockForUpdate(null);
                } else if (recipe != null) {
                    if (processMax == 0) {
                        this.process = recipe.time;
                        this.processMax = process;
                    } else {
                        Utils.modifyInvStackSize(inventory, 0, -recipe.input.getCount());
                        tank.drain(recipe.input_fluid.getAmount(), IFluidHandler.FluidAction.EXECUTE);
                        if (!inventory.get(1).isEmpty())
                            inventory.get(1).grow(recipe.output.copy().getCount());
                        else if (inventory.get(1).isEmpty())
                            inventory.set(1, recipe.output.copy());
                        processMax = 0;
                    }
                }
            } else if (process > 0) {
                process = processMax;
                this.markContainingBlockForUpdate(null);
            }
        }
    }

    @Nullable
    public ElectrolyzerRecipe getRecipe() {
        if (inventory.get(0).isEmpty())
            return null;
        ElectrolyzerRecipe recipe = ElectrolyzerRecipe.findRecipe(inventory.get(0), null, tank.getFluid());
        if (recipe == null && recipe.flag)
            return null;
        if (inventory.get(1).isEmpty() || (ItemStack.areItemsEqual(inventory.get(1), recipe.output) &&
                inventory.get(1).getCount() + recipe.output.getCount() <= getSlotLimit(1))) {
            return recipe;
        }
        return null;
    }

    @Override
    public int[] getCurrentProcessesStep() {
        return new int[]{processMax - process};
    }

    @Override
    public int[] getCurrentProcessesMax() {
        return new int[]{processMax};
    }


    public LazyOptional<IFluidHandler> fluidHandler = registerConstantCap(new FluidHandler(this));
    LazyOptional<IItemHandler> invHandler = registerConstantCap(
            new IEInventoryHandler(2, this, 0, new boolean[]{true, false},
                    new boolean[]{false, true})
    );

    @Nonnull
    @Override
    public <C> LazyOptional<C> getCapability(@Nonnull Capability<C> capability, @Nullable Direction facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
            return fluidHandler.cast();
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)
            return invHandler.cast();
        return super.getCapability(capability, facing);
    }

    @Nonnull
    @Override
    public FluxStorage getFluxStorage() {
        return energyStorage;
    }

    @Nonnull
    @Override
    public IEEnums.IOSideConfig getEnergySideConfig(@Nullable Direction facing) {
        return IEEnums.IOSideConfig.INPUT;
    }

    EnergyHelper.IEForgeEnergyWrapper wrapper = new EnergyHelper.IEForgeEnergyWrapper(this, null);

    @Nullable
    @Override
    public EnergyHelper.IEForgeEnergyWrapper getCapabilityWrapper(Direction facing) {
        return wrapper;
    }

    static class FluidHandler implements IFluidHandler {
        ElectrolyzerTileEntity tile;

        @Nullable
        FluidHandler(ElectrolyzerTileEntity tile) {
            this.tile = tile;
        }

        @Override
        public int fill(FluidStack resource, FluidAction doFill) {
            if (resource == null)
                return 0;

            int i = tile.tank.fill(resource, doFill);
            if (i > 0) {
                tile.markDirty();
                tile.markContainingBlockForUpdate(null);
            }
            return i;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction doDrain) {
            if (resource == null)
                return FluidStack.EMPTY;
            return this.drain(resource.getAmount(), doDrain);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction doDrain) {
            FluidStack f = tile.tank.drain(maxDrain, doDrain);
            if (!f.isEmpty()) {
                tile.markDirty();
                tile.markContainingBlockForUpdate(null);
            }
            return f;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return tile.tank.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tile.tank.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return tile.tank.isFluidValid(tank, stack);
        }
    }

    @Nullable
    @Override
    public NonNullList<ItemStack> getInventory() {
        return this.inventory;
    }

    @Override
    public boolean isStackValid(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public void doGraphicalUpdates() {

    }

    @Override
    public Property<Direction> getFacingProperty() {
        return IEProperties.FACING_ALL;
    }

    @Override
    public PlacementLimitation getFacingLimitation() {
        return PlacementLimitation.PISTON_LIKE;
    }

    @Nullable
    @Override
    public IEBlockInterfaces.IInteractionObjectIE getGuiMaster() {
        return this;
    }

    @Override
    public boolean canUseGui(PlayerEntity player) {
        return true;
    }
}
