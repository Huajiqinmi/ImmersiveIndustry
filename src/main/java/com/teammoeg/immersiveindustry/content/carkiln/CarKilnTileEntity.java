package com.teammoeg.immersiveindustry.content.carkiln;

import blusunrize.immersiveengineering.api.IEEnums;
import blusunrize.immersiveengineering.api.energy.immersiveflux.FluxStorage;
import blusunrize.immersiveengineering.api.energy.immersiveflux.FluxStorageAdvanced;
import blusunrize.immersiveengineering.api.utils.CapabilityReference;
import blusunrize.immersiveengineering.api.utils.DirectionalBlockPos;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces;
import blusunrize.immersiveengineering.common.blocks.generic.MultiblockPartTileEntity;
import blusunrize.immersiveengineering.common.util.EnergyHelper;
import blusunrize.immersiveengineering.common.util.Utils;
import blusunrize.immersiveengineering.common.util.inventory.IEInventoryHandler;
import blusunrize.immersiveengineering.common.util.inventory.IIEInventory;
import com.google.common.collect.ImmutableSet;
import com.teammoeg.immersiveindustry.IIConfig;
import com.teammoeg.immersiveindustry.IIContent.IIMultiblocks;
import com.teammoeg.immersiveindustry.IIContent.IITileTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

public class CarKilnTileEntity extends MultiblockPartTileEntity<CarKilnTileEntity>
		implements IEBlockInterfaces.IBlockBounds, EnergyHelper.IIEInternalFluxHandler, IIEInventory,
		IEBlockInterfaces.IInteractionObjectIE, IEBlockInterfaces.IProcessTile {
	public int processMax = 0;
	public int process = 0;
	int pos;//animation process from 0-52, 0=idle 52=working
	boolean active;
	private static BlockPos itmeout = new BlockPos(1, 0, 5);
	private NonNullList<ItemStack> inventory = NonNullList.withSize(9, ItemStack.EMPTY);
	public FluxStorageAdvanced energyStorage = new FluxStorageAdvanced(32000);
	EnergyHelper.IEForgeEnergyWrapper wrapper = new EnergyHelper.IEForgeEnergyWrapper(this, null);

	public FluidTank[] tankinput = new FluidTank[]{new FluidTank(16000)};

	public CarKilnTileEntity() {
		super(IIMultiblocks.CAR_KILN, IITileTypes.CAR_KILN.get(), false);
	}

	private CapabilityReference<IItemHandler> outputItemCap = CapabilityReference.forTileEntityAt(this,
			() -> {
				Direction fw = getFacing().rotateY();
				return new DirectionalBlockPos(this.getBlockPosForPos(itmeout), fw);
			}, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);

	@Override
	public AxisAlignedBB getRenderBoundingBox() {
		BlockPos bp = this.getPos();
		return new AxisAlignedBB(bp.getX() - (getFacing().getAxis() == Axis.Z ? 1 : 2),
				bp.getY(),
				bp.getZ() - (getFacing().getAxis() == Axis.X ? 1 : 2),
				bp.getX() + (getFacing().getAxis() == Axis.Z ? 2 : 1),
				bp.getY() + 3,
				bp.getZ() + (getFacing().getAxis() == Axis.X ? 2 : 1));
	}

	@Override
	public void tick() {
		checkForNeedlessTicking();
		checkForNeedlessTicking();
		if (!isDummy()) {
			if (!world.isRemote) {
				int energyConsume = IIConfig.COMMON.carKilnConsume.get();
				if (!isRSDisabled() && energyStorage.getEnergyStored() >= energyConsume) {
					if (process > 0) {
						process--;
						energyStorage.extractEnergy(energyConsume, false);
						if (!active)
							active = true;

						this.markContainingBlockForUpdate(null);
						return;
					}
//					if (!inventory.get(2).isEmpty()) {
//						RotaryKilnRecipe recipe = getRecipe(2);
//						if (recipe != null) {
//							int count = inventory.get(2).getCount() / recipe.input.getCount();
//							ItemStack result = recipe.output.copy();
//							result.setCount(count);
//							active = false;
//							if (inventory.get(3).isEmpty()) {
//								inventory.set(3, result);
//								inventory.set(2, ItemStack.EMPTY);
//								this.markContainingBlockForUpdate(null);
//							} else if (inventory.get(3).isItemEqual(recipe.output)
//									&& inventory.get(3).getCount() + result.getCount() <= getSlotLimit(3)) {
//								inventory.get(3).grow(result.getCount());
//								inventory.set(2, ItemStack.EMPTY);
//								this.markContainingBlockForUpdate(null);
//							} else return;
//							if (!recipe.output_fluid.isEmpty()) {
//								FluidStack fluidStack = recipe.output_fluid.copy();
//								fluidStack.setAmount(fluidStack.getAmount() * count);
//								tankinput[0].fill(fluidStack, IFluidHandler.FluidAction.EXECUTE);
//							}
//						}
//					}
//					if (!inventory.get(0).isEmpty() || !inventory.get(1).isEmpty()) {
//						inventory.set(2, inventory.get(1));
//						inventory.set(1, inventory.get(0).split(16));
//						process = 300;
//					}
				} else if (active) {
					active = false;
					this.markContainingBlockForUpdate(null);
				}
			} else {
				if (active && pos < 53)
					pos++;
				else if (pos > 0)
					pos--;
			}
		}
	}

	public void tryOutput() {
		if (this.world.getGameTime() % 8L == 0L) {
			if (this.outputItemCap.isPresent()) {

				for (int slot = 4; slot < 9; ++slot) {
					if (!this.inventory.get(slot).isEmpty()) {
						ItemStack stack = ItemHandlerHelper.copyStackWithSize(this.inventory.get(slot), 1);
						stack = Utils.insertStackIntoInventory(this.outputItemCap, stack, false);
						if (stack.isEmpty()) {
							this.inventory.get(slot).shrink(1);
							if (this.inventory.get(slot).getCount() <= 0) {
								this.inventory.set(slot, ItemStack.EMPTY);
							}
						}
					}
				}
			}
		}
	}
	@Override
	protected boolean canDrainTankFrom(int arg0, Direction arg1) {
		return false;
	}

	@Override
	protected boolean canFillTankFrom(int i, Direction side, FluidStack fluidStack) {
		CarKilnTileEntity master = master();
		if (master != null) {
			return true;
		}
		return false;
	}

	@Override
	protected IFluidTank[] getAccessibleFluidTanks(Direction side) {
		CarKilnTileEntity master = master();
		if (master != null) {
			if (this.posInMultiblock.getY() == 3 && this.posInMultiblock.getX() == 1 && this.posInMultiblock.getZ() == 0
					&& (side == null || side == getFacing())) {
				return master.tankinput;
			}
		}
		return new FluidTank[0];
	}

	@Nonnull
	@Override
	public VoxelShape getBlockBounds(@Nullable ISelectionContext iSelectionContext) {
		return VoxelShapes.fullCube();
	}

	@Nullable
	@Override
	public IEBlockInterfaces.IInteractionObjectIE getGuiMaster() {
		return master();
	}

	@Override
	public boolean canUseGui(PlayerEntity playerEntity) {
		return formed;
	}

	@Nonnull
	@Override
	public IEEnums.IOSideConfig getEnergySideConfig(Direction facing) {
		return this.formed && this.isEnergyPos() ? IEEnums.IOSideConfig.INPUT : IEEnums.IOSideConfig.NONE;
	}

	public Set<BlockPos> getEnergyPos() {
		return ImmutableSet.of(new BlockPos(1, 1, 0));
	}

	@Override
	public void postEnergyTransferUpdate(int energy, boolean simulate) {
		if (!simulate) {
			this.updateMasterBlock(null, energy != 0);
		}
	}

	@Override
	public Set<BlockPos> getRedstonePos() {
		return ImmutableSet.of(new BlockPos(0, 1, 2));
	}

	public boolean isEnergyPos() {
		return this.getEnergyPos().contains(this.posInMultiblock);
	}

	@Nonnull
	@Override
	public FluxStorage getFluxStorage() {
		CarKilnTileEntity master = this.master();
		return master != null ? master.energyStorage : this.energyStorage;
	}


	@Nullable
	@Override
	public EnergyHelper.IEForgeEnergyWrapper getCapabilityWrapper(Direction facing) {
		return this.formed && this.isEnergyPos() ? this.wrapper : null;
	}

	@Nullable
	@Override
	public NonNullList<ItemStack> getInventory() {
		if (master() != null)
			return master().inventory;
		return this.inventory;
	}

	@Override
	public void readCustomNBT(CompoundNBT nbt, boolean descPacket) {
		super.readCustomNBT(nbt, descPacket);
		energyStorage.readFromNBT(nbt);
		tankinput[0].readFromNBT(nbt.getCompound("tankinput"));
		active = nbt.getBoolean("active");
		if (!descPacket)
			ItemStackHelper.loadAllItems(nbt, inventory);
	}

    @Override
    public void writeCustomNBT(CompoundNBT nbt, boolean descPacket) {
		super.writeCustomNBT(nbt, descPacket);
		energyStorage.writeToNBT(nbt);
		nbt.put("tankinput", tankinput[0].writeToNBT(new CompoundNBT()));
		nbt.putBoolean("active", active);
		if (!descPacket)
			ItemStackHelper.saveAllItems(nbt, inventory);
	}

    LazyOptional<IItemHandler> inHandler = registerConstantCap(new IEInventoryHandler(1, this, 0, true, false));
    LazyOptional<IItemHandler> outHandler = registerConstantCap(new IEInventoryHandler(1, this, 4, false, true));

    @Nonnull
    @Override
    public <C> LazyOptional<C> getCapability(@Nonnull Capability<C> capability, @Nullable Direction facing) {

        if (facing != null && facing.getYOffset() == 0 && this.posInMultiblock.getZ() == 4) {
            if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
                if (this.posInMultiblock.getY() == 1)
                    return inHandler.cast();
                else if (this.posInMultiblock.getX() == 1)
                    return outHandler.cast();
                return LazyOptional.empty();
            }
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public boolean isStackValid(int i, ItemStack itemStack) {
        return true;
	}

	@Override
	public int getSlotLimit(int i) {
		return 64;
	}

	@Override
	public void doGraphicalUpdates() {

	}

	@Override
	public int[] getCurrentProcessesStep() {
		CarKilnTileEntity master = master();
		if (master != this && master != null)
			return master.getCurrentProcessesStep();
		return new int[]{processMax - process};
	}

	@Override
	public int[] getCurrentProcessesMax() {
		CarKilnTileEntity master = master();
		if (master != this && master != null)
			return master.getCurrentProcessesMax();
		return new int[]{processMax};
	}
}
