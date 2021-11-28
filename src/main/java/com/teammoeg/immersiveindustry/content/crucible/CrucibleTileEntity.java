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

package com.teammoeg.immersiveindustry.content.crucible;

import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces;
import blusunrize.immersiveengineering.common.blocks.generic.MultiblockPartTileEntity;
import blusunrize.immersiveengineering.common.blocks.metal.BlastFurnacePreheaterTileEntity;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.inventory.IEInventoryHandler;
import blusunrize.immersiveengineering.common.util.inventory.IIEInventory;
import com.teammoeg.immersiveindustry.IIContent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.IIntArray;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

public class CrucibleTileEntity extends MultiblockPartTileEntity<CrucibleTileEntity> implements IIEInventory,
        IIBlockInterfaces.IActiveState, IEBlockInterfaces.IInteractionObjectIE, IEBlockInterfaces.IProcessTile, IEBlockInterfaces.IBlockBounds {

    public CrucibleTileEntity.CrucibleData guiData = new CrucibleTileEntity.CrucibleData();
    private NonNullList<ItemStack> inventory = NonNullList.withSize(4, ItemStack.EMPTY);
    public int temperature;
    public int burnTime;
    public int process = 0;
    public int processMax = 0;
    public int updatetick = 0;
    public static ResourceLocation coal_coke = new ResourceLocation("forge:coal_coke");

    public CrucibleTileEntity() {
        super(IIContent.IIMultiblocks.CRUCIBLE, IIContent.IITileTypes.CRUCIBLE.get(), false);
    }
    @Override
	public void disassemble()
	{
		if(formed&&!world.isRemote)
		{
			tempMasterTE = master();
			BlockPos startPos = getOrigin();
			multiblockInstance.disassemble(world, startPos, getIsMirrored(), multiblockInstance.untransformDirection(getFacing()));
			world.destroyBlock(pos, false);
		}
	}

    @Nonnull
    @Override
    public IFluidTank[] getAccessibleFluidTanks(Direction side) {
        return new IFluidTank[0];
    }

    @Override
    public boolean canFillTankFrom(int iTank, Direction side, FluidStack resource) {
        return false;
    }

    @Override
    public boolean canDrainTankFrom(int iTank, Direction side) {
        return false;
    }

    @Nonnull
    @Override
    public VoxelShape getBlockBounds(@Nullable ISelectionContext ctx) {
        return VoxelShapes.fullCube();
    }

    @Override
    public boolean receiveClientEvent(int id, int arg) {
        if (id == 0)
            this.formed = arg == 1;
        markDirty();
        this.markContainingBlockForUpdate(null);
        return true;
    }

    @Nullable
    @Override
    public IEBlockInterfaces.IInteractionObjectIE getGuiMaster() {
        return master();
    }

    @Override
    public boolean canUseGui(PlayerEntity player) {
        return formed;
    }

    @Override
    public int[] getCurrentProcessesStep() {
        CrucibleTileEntity master = master();
        if (master != this && master != null)
            return master.getCurrentProcessesStep();
        return new int[]{processMax - process};
    }

    @Override
    public int[] getCurrentProcessesMax() {
        CrucibleTileEntity master = master();
        if (master != this && master != null)
            return master.getCurrentProcessesMax();
        return new int[]{processMax};
    }

    @Nullable
    @Override
    public NonNullList<ItemStack> getInventory() {
        CrucibleTileEntity master = master();
        if (master != null && master.formed && formed)
            return master.inventory;
        return this.inventory;
    }

    @Override
    public boolean isStackValid(int slot, ItemStack stack) {
        if (stack.isEmpty())
            return false;
        if (slot == 0)
            return CrucibleRecipe.isValidRecipeInput(stack, true);
        if (slot == 1)
            return CrucibleRecipe.isValidRecipeInput(stack, false);
        if (slot == 3)
            return stack.getItem().getTags().contains(coal_coke);
        return false;
    }


    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public void doGraphicalUpdates() {

    }

    LazyOptional<IItemHandler> invHandler = registerConstantCap(
            new IEInventoryHandler(4, this, 0, new boolean[]{true, true, false, true},
                    new boolean[]{false, false, true, false})
    );

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, Direction facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            CrucibleTileEntity master = master();
            if (master != null)
                return master.invHandler.cast();
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public void readCustomNBT(CompoundNBT nbt, boolean descPacket) {
        super.readCustomNBT(nbt, descPacket);
        temperature=nbt.getInt("temperature");
        burnTime=nbt.getInt("burntime");
        if (!descPacket) {
            ItemStackHelper.loadAllItems(nbt, inventory);
            process = nbt.getInt("process");
            processMax = nbt.getInt("processMax");
        }
    }

    @Override
    public void writeCustomNBT(CompoundNBT nbt, boolean descPacket) {
        super.writeCustomNBT(nbt, descPacket);
        nbt.putInt("temperature", temperature);
        nbt.putInt("burntime", burnTime);
        if (!descPacket) {
            nbt.putInt("process", process);
            nbt.putInt("processMax", processMax);
            ItemStackHelper.saveAllItems(nbt, inventory);
        }
    }

    public void setTemperature(int temperature) {
        if (master() != null)
            master().temperature = temperature;
    }

    public void setBurnTime(int burnTime) {
        if (master() != null)
            master().burnTime = burnTime;
    }
    boolean uncheckedLocation=true;
    @Override
    public void tick() {
    	if(uncheckedLocation&&!world.isRemote) {
    		if(master()==null) {
    			this.setFacing(this.getFacing().getOpposite());
    			this.offsetToMaster=new BlockPos(-offsetToMaster.getX(),offsetToMaster.getY(),-offsetToMaster.getZ());
    			if(master()==null) {
    				this.setFacing(this.getFacing().getOpposite());
    				this.offsetToMaster=new BlockPos(-offsetToMaster.getX(),offsetToMaster.getY(),-offsetToMaster.getZ());
    			}else {
    				this.markContainingBlockForUpdate(null);
    			}
    		}
    		uncheckedLocation=false;
    	}
        checkForNeedlessTicking();
        
        if (!world.isRemote && formed && !isDummy()) {
            CrucibleRecipe recipe = getRecipe();
            updatetick++;
            if (updatetick > 10) {
                final boolean activeBeforeTick = getIsActive();
                if (temperature > 0) {
                    updatetick = 0;
                    this.markContainingBlockForUpdate(null);
                    if (!activeBeforeTick)
                        setActive(true);
                } else if (activeBeforeTick)
                    setActive(false);
                final boolean activeAfterTick = getIsActive();
                if (activeBeforeTick != activeAfterTick) {
                    this.markDirty();
                    // scan 3x4x3
                    for (int x = 0; x < 3; ++x)
                        for (int y = 0; y < 4; ++y)
                            for (int z = 0; z < 3; ++z) {
                                BlockPos actualPos = getBlockPosForPos(new BlockPos(x, y, z));
                                TileEntity te = Utils.getExistingTileEntity(world, actualPos);
                                if (te instanceof CrucibleTileEntity)
                                    ((CrucibleTileEntity) te).setActive(activeAfterTick);
                            }
                }
            }
            if (burnTime > 0 && temperature < 1600) {
                if (getFromPreheater(BlastFurnacePreheaterTileEntity::doSpeedup, 0) > 0)
                    temperature++;
                else if (temperature < 1100)
                    temperature++;
            } else if (burnTime <= 0 && temperature > 0) {
                temperature--;
            }
            if (burnTime > 0) {
                burnTime--;
            } else {
                if (inventory.get(3).getItem().getTags().contains(coal_coke)) {
                    burnTime = 600;
                    inventory.get(3).shrink(1);
                    this.markDirty();
                }
            }
            if (temperature > 1400) {
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
                            getFromPreheater(BlastFurnacePreheaterTileEntity::doSpeedup, 0);
                        }
                    }
                    this.markContainingBlockForUpdate(null);
                } else if (recipe != null) {
                    if (processMax == 0) {
                        this.process = recipe.time;
                        this.processMax = process;
                    } else {
                        Utils.modifyInvStackSize(inventory, 0, -recipe.input.getCount());
                        Utils.modifyInvStackSize(inventory, 1, -recipe.input2.getCount());
                        if (!inventory.get(2).isEmpty())
                            inventory.get(2).grow(recipe.output.copy().getCount());
                        else if (inventory.get(2).isEmpty())
                            inventory.set(2, recipe.output.copy());
                        processMax = 0;
                    }
                }
            }
        }
        if (world != null && world.isRemote && formed && !isDummy() && getIsActive()) {
            Random random = world.rand;
            if (random.nextFloat() < 0.4F) {
                for (int i = 0; i < random.nextInt(2) + 2; ++i) {
                    world.addOptionalParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, (double) pos.getX() + 0.5D + random.nextDouble() / 3.0D * (double) (random.nextBoolean() ? 1 : -1), (double) pos.getY() + random.nextDouble() + random.nextDouble(), (double) pos.getZ() + 0.5D + random.nextDouble() / 3.0D * (double) (random.nextBoolean() ? 1 : -1), 0.0D, 0.05D, 0.0D);
                    world.addParticle(ParticleTypes.SMOKE, (double) pos.getX() + 0.25D + random.nextDouble() / 2.0D * (double) (random.nextBoolean() ? 1 : -1), (double) pos.getY() + 0.4D, (double) pos.getZ() + 0.25D + random.nextDouble() / 2.0D * (double) (random.nextBoolean() ? 1 : -1), 0.002D, 0.01D, 0.0D);
                }
            }
        }
    }

    @Nullable
    public CrucibleRecipe getRecipe() {
        if (inventory.get(0).isEmpty())
            return null;
        CrucibleRecipe recipe = CrucibleRecipe.findRecipe(inventory.get(0), inventory.get(1));
        if (recipe == null)
            return null;
        if (inventory.get(2).isEmpty() || (ItemStack.areItemsEqual(inventory.get(2), recipe.output) &&
                inventory.get(2).getCount() + recipe.output.getCount() <= getSlotLimit(2))) {
            return recipe;
        }
        return null;
    }

    public class CrucibleData implements IIntArray {
        public static final int BURN_TIME = 0;
        public static final int PROCESS_MAX = 1;
        public static final int CURRENT_PROCESS = 2;

        @Override
        public int get(int index) {
            switch (index) {
                case BURN_TIME:
                    return burnTime;
                case PROCESS_MAX:
                    return processMax;
                case CURRENT_PROCESS:
                    return process;
                default:
                    throw new IllegalArgumentException("Unknown index " + index);
            }
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case BURN_TIME:
                    burnTime = value;
                    break;
                case PROCESS_MAX:
                    processMax = value;
                    break;
                case CURRENT_PROCESS:
                    process = value;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown index " + index);
            }
        }

        @Override
        public int size() {
            return 3;
        }
    }

    public <V> V getFromPreheater(Function<BlastFurnacePreheaterTileEntity, V> getter, V orElse) {
        return getBlast().map(getter).orElse(orElse);
    }

    public Optional<BlastFurnacePreheaterTileEntity> getBlast() {
        BlockPos pos = getPos().add(0, -1, 0).offset(getFacing(), 2);
        TileEntity te = Utils.getExistingTileEntity(world, pos);
        if (te instanceof BlastFurnacePreheaterTileEntity)
            return Optional.of((BlastFurnacePreheaterTileEntity) te);
        return Optional.empty();
    }
}
