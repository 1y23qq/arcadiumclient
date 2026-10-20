package tektonikal.customblockhighlight.legacy;

//? if = 1.8.9 {
/*import net.minecraft.block.Block;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import tektonikal.customblockhighlight.legacy.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import tektonikal.customblockhighlight.legacy.BlockPos;
import net.minecraft.util.math.Box;
import tektonikal.customblockhighlight.legacy.Direction;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import tektonikal.customblockhighlight.legacy.phys.AABB;
import tektonikal.customblockhighlight.legacy.phys.Vec3;
import tektonikal.customblockhighlight.legacy.phys.shapes.Shapes;
import tektonikal.customblockhighlight.legacy.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class LegacyWorld {
	public static boolean pickAnyFluid;

	private LegacyWorld() {
	}

	public static BlockState getState(BlockPos pos) {
		return Minecraft.getInstance().level.getBlockState(pos);
	}

	public static boolean isEmpty(BlockPos pos) {
		return Minecraft.getInstance().level.isEmptyBlock(pos);
	}

	public static VoxelShape getShape(BlockPos pos) {
		ClientWorld world = Minecraft.getInstance().level;
		if (world == null) return Shapes.empty();
		BlockState state = world.getBlockState(pos);
		Block block = state.getBlock();
		Material material = block.getMaterial();
		if (material == Material.AIR) return Shapes.empty();
		if (material.isLiquid()) {
			return Shapes.box(0, 0, 0, 1, fluidHeight(world, pos, state), 1);
		}

		block.updateShape(world, pos);
		AABB outline = AABB.of(block.getOutlineShape(world, pos)).move(-pos.getX(), -pos.getY(), -pos.getZ());
		List<Box> collisions = new ArrayList<>();
		Box mask = new Box(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1, pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
		block.addCollisions(world, pos, state, mask, collisions, Minecraft.getInstance().player);
		// addCollisions changes the block's shared bounds for multi-box blocks, restore them for vanilla's outline
		block.updateShape(world, pos);

		if (collisions.size() > 1) {
			List<AABB> boxes = new ArrayList<>(collisions.size());
			for (Box collision : collisions) {
				boxes.add(AABB.of(collision).move(-pos.getX(), -pos.getY(), -pos.getZ()).intersect(outline));
			}
			VoxelShape shape = Shapes.create(boxes);
			if (!shape.isEmpty()) return shape;
		}
		return Shapes.create(outline);
	}

	private static double fluidHeight(ClientWorld world, BlockPos pos, BlockState state) {
		if (world.getBlockState(pos.above()).getBlock().getMaterial() == state.getBlock().getMaterial()) return 1;
		return 1 - LiquidBlock.getHeightLoss(state.get(LiquidBlock.LEVEL));
	}

	public static Vec3 getOffset(BlockPos pos) {
		ClientWorld world = Minecraft.getInstance().level;
		if (world == null) return Vec3.ZERO;
		Block.OffsetType type = world.getBlockState(pos).getBlock().getOffsetType();
		if (type == Block.OffsetType.NONE) return Vec3.ZERO;
		long seed = MathHelper.hashCode(pos);
		double x = ((float) (seed >> 16 & 15L) / 15.0F - 0.5) * 0.5;
		double z = ((float) (seed >> 24 & 15L) / 15.0F - 0.5) * 0.5;
		double y = type == Block.OffsetType.XYZ ? ((float) (seed >> 20 & 15L) / 15.0F - 1.0) * 0.2 : 0;
		return new Vec3(x, y, z);
	}

	public static BlockState getActualState(BlockPos pos) {
		ClientWorld world = Minecraft.getInstance().level;
		BlockState state = world.getBlockState(pos);
		return state.getBlock().resolveVirtualProperties(state, world, pos);
	}

	public static Quaternionf rotation(Direction direction) {
		return switch (direction) {
			case DOWN -> new Quaternionf().rotationX((float) Math.PI);
			case UP -> new Quaternionf();
			case NORTH -> new Quaternionf().rotationXYZ((float) (Math.PI / 2), 0.0F, (float) Math.PI);
			case SOUTH -> new Quaternionf().rotationX((float) (Math.PI / 2));
			case WEST -> new Quaternionf().rotationXYZ((float) (Math.PI / 2), 0.0F, (float) (Math.PI / 2));
			case EAST -> new Quaternionf().rotationXYZ((float) (Math.PI / 2), 0.0F, (float) (-Math.PI / 2));
		};
	}
}
*///? } else if = 1.7.10 {
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BedBlock;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.PistonBaseBlock;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import tektonikal.customblockhighlight.mixin.TesselatorCaptureAccessor;
import tektonikal.customblockhighlight.legacy.phys.AABB;
import tektonikal.customblockhighlight.legacy.phys.Vec3;
import tektonikal.customblockhighlight.legacy.phys.shapes.Shapes;
import tektonikal.customblockhighlight.legacy.phys.shapes.VoxelShape;

public final class LegacyWorld {
    public static boolean pickAnyFluid;
    private static final Map<BlockPos, BlockState> STATES = new HashMap<>();
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static WeakReference<ClientWorld> cachedWorld = new WeakReference<>(null);
    private static long cachedTick = Long.MIN_VALUE;

    private LegacyWorld() {}

    public static BlockState getState(BlockPos pos) {
        ClientWorld world = Minecraft.getInstance().level;
        long tick = world.getTime();
        if (cachedWorld.get() != world || cachedTick != tick) {
            STATES.clear();
            cachedWorld = new WeakReference<>(world);
            cachedTick = tick;
        }
        BlockState existing = STATES.get(pos);
        if (existing != null) return existing;
        BlockState state = new BlockState(pos, world.getBlock(pos.x(), pos.y(), pos.z()),
                world.getBlockMetadata(pos.x(), pos.y(), pos.z()));
        STATES.put(pos, state);
        return state;
    }

    public static boolean isEmpty(BlockPos pos) {
        return Minecraft.getInstance().level.isEmptyBlock(pos.x(), pos.y(), pos.z());
    }

    public static VoxelShape getShape(BlockPos pos) {
        ClientWorld world = Minecraft.getInstance().level;
        if (world == null) return Shapes.empty();
        BlockState state = getState(pos);
        if (state.shape != null) return state.shape;
        Block block = state.block;
        Material material = block.getMaterial();
        if (material == Material.AIR) return state.shape = Shapes.empty();
        if (material.isLiquid()) {
            double height = world.getBlock(pos.x(), pos.y() + 1, pos.z()).getMaterial() == material
                    ? 1 : 1 - LiquidBlock.getHeightLoss(state.metadata);
            return state.shape = Shapes.box(0, 0, 0, 1, height, 1);
        }
        block.updateShape(world, pos.x(), pos.y(), pos.z());
        AABB outline = AABB.of(block.getOutlineShape(world, pos.x(), pos.y(), pos.z())).move(-pos.x(), -pos.y(), -pos.z());
        List<Box> collisions = new ArrayList<>();
        Box mask = Box.of(pos.x() - 1, pos.y() - 1, pos.z() - 1, pos.x() + 2, pos.y() + 2, pos.z() + 2);
        block.addCollisions(world, pos.x(), pos.y(), pos.z(), mask, collisions, Minecraft.getInstance().player);
        block.updateShape(world, pos.x(), pos.y(), pos.z());
        if (collisions.size() > 1) {
            List<AABB> boxes = new ArrayList<>(collisions.size());
            for (Box collision : collisions) boxes.add(AABB.of(collision).move(-pos.x(), -pos.y(), -pos.z()).intersect(outline));
            VoxelShape shape = Shapes.create(boxes);
            if (!shape.isEmpty()) return state.shape = shape;
        }
        return state.shape = Shapes.create(outline);
    }

    public static Vec3 getOffset(BlockPos pos) {
        return Vec3.ZERO;
    }

    public static BlockState getActualState(BlockPos pos) {
        return getState(pos);
    }

    public static int[] getModelVertices(BlockState state) {
        if (state.modelVertices != null) return state.modelVertices;
        ClientWorld world = Minecraft.getInstance().level;
        Tesselator tesselator = Tesselator.INSTANCE;
        TesselatorCaptureAccessor access = (TesselatorCaptureAccessor) tesselator;
        if (access.cbh$isTesselating()) throw new IllegalStateException("Nested block model capture");
        double oldX = access.cbh$offsetX(), oldY = access.cbh$offsetY(), oldZ = access.cbh$offsetZ();
        BlockPos pos = state.position;
        GL11.glPushAttrib(GL11.GL_CURRENT_BIT);
        try {
            tesselator.begin();
            tesselator.offset(-pos.x(), -pos.y(), -pos.z());
            state.block.updateShape(world, pos.x(), pos.y(), pos.z());
            BlockRenderer renderer = new BlockRenderer(world);
            renderer.setRenderFaceAlways(true);
            renderer.tesselateInWorld(state.block, pos.x(), pos.y(), pos.z());
            return state.modelVertices = Arrays.copyOf(access.cbh$vertices(), access.cbh$vertexCount() * 8);
        } finally {
            access.cbh$setTesselating(false);
            access.cbh$clear();
            tesselator.offset(oldX, oldY, oldZ);
            state.block.updateShape(world, pos.x(), pos.y(), pos.z());
            GL11.glPopAttrib();
        }
    }

    public static Direction joinConnected(BlockPos pos) {
        BlockState state = getState(pos);
        Block block = state.block;
        int metadata = state.metadata;
        if (block instanceof DoorBlock || block instanceof DoublePlantBlock) {
            Direction direction = (metadata & 8) == 0 ? Direction.UP : Direction.DOWN;
            BlockState neighbor = getState(pos.relative(direction));
            return neighbor.block == block && ((metadata ^ neighbor.metadata) & 8) != 0 ? direction : null;
        }
        if (block instanceof ChestBlock) {
            for (Direction direction : HORIZONTAL)
                if (getState(pos.relative(direction)).block == block) return direction;
        }
        if (block instanceof BedBlock) {
            Direction direction = switch (metadata & 3) {
                case 0 -> Direction.SOUTH;
                case 1 -> Direction.WEST;
                case 2 -> Direction.NORTH;
                default -> Direction.EAST;
            };
            if ((metadata & 8) != 0) direction = direction.getOpposite();
            BlockState neighbor = getState(pos.relative(direction));
            return neighbor.block instanceof BedBlock && ((metadata ^ neighbor.metadata) & 8) != 0 ? direction : null;
        }
        if (block instanceof PistonHeadBlock) {
            Direction facing = Direction.byId(metadata & 7);
            Direction direction = facing.getOpposite();
            BlockState neighbor = getState(pos.relative(direction));
            return neighbor.block instanceof PistonBaseBlock && (neighbor.metadata & 7) == (metadata & 7) ? direction : null;
        }
        if (block instanceof PistonBaseBlock && (metadata & 8) != 0) {
            Direction direction = Direction.byId(metadata & 7);
            BlockState neighbor = getState(pos.relative(direction));
            return neighbor.block instanceof PistonHeadBlock && (neighbor.metadata & 7) == (metadata & 7) ? direction : null;
        }
        return null;
    }

    public static Quaternionf rotation(Direction direction) {
        return switch (direction) {
            case DOWN -> new Quaternionf().rotationX((float) Math.PI);
            case UP -> new Quaternionf();
            case NORTH -> new Quaternionf().rotationXYZ((float) (Math.PI / 2), 0, (float) Math.PI);
            case SOUTH -> new Quaternionf().rotationX((float) (Math.PI / 2));
            case WEST -> new Quaternionf().rotationXYZ((float) (Math.PI / 2), 0, (float) (Math.PI / 2));
            case EAST -> new Quaternionf().rotationXYZ((float) (Math.PI / 2), 0, (float) (-Math.PI / 2));
        };
    }
}
//? }
