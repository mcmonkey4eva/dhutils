package me.desht.dhutils.nms.v1_8_R3;

import me.desht.dhutils.nms.api.NMSAbstraction;
import net.minecraft.server.v1_8_R3.*;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.v1_8_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NMSHandler implements NMSAbstraction {

    private static Field CHUNK_F_INT_ARRAY = null;
    private static Method CHUNK_D_THREE_INT_METHOD = null;
    private static Method CHUNK_D_TWO_INT_METHOD = null;
    static {
        try {
            Class<Chunk> chunk = Chunk.class;
            CHUNK_F_INT_ARRAY = chunk.getDeclaredField("f");
            CHUNK_F_INT_ARRAY.setAccessible(true);
            CHUNK_D_THREE_INT_METHOD = chunk.getDeclaredMethod("d", int.class, int.class, int.class);
            CHUNK_D_THREE_INT_METHOD.setAccessible(true);
            CHUNK_D_TWO_INT_METHOD = chunk.getDeclaredMethod("d", int.class, int.class);
            CHUNK_D_TWO_INT_METHOD.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int getChunkFIntArray(Chunk that, int index) {
        try {
            return ((int[]) CHUNK_F_INT_ARRAY.get(that))[index];
        } catch (Exception e) {
            System.out.println("Reflection exception: " + e);
            return 0;
        }
    }

    private static void setChunkFIntArray(Chunk that, int index, int set) {
        try {
            ((int[]) CHUNK_F_INT_ARRAY.get(that))[index] = set;
        } catch (Exception e) {
            System.out.println("Reflection exception: " + e);
        }
    }

    private void invokeChunkDThreeIntMethod(Chunk nmsChunk, int i, int j, int k) {
        try {
            CHUNK_D_THREE_INT_METHOD.invoke(nmsChunk, i, j, k);
        } catch (Exception e) {
            System.out.println("Reflection exception: " + e);
        }
    }

    private void invokeChunkDTwoIntMethod(Chunk nmsChunk, int i, int j) {
        try {
            CHUNK_D_TWO_INT_METHOD.invoke(nmsChunk, i, j);
        } catch (Exception e) {
            System.out.println("Reflection exception: " + e);
        }
    }

	@Override
    public boolean setBlockFast(World world, int x, int y, int z, int blockId, byte data) {
        net.minecraft.server.v1_8_R3.World w = ((CraftWorld) world).getHandle();
        Chunk chunk = w.getChunkAt(x >> 4, z >> 4);
        return a(chunk, new BlockPosition(x, y, z), Block.getById(blockId).fromLegacyData(data));
    }

    private boolean a(Chunk that, BlockPosition blockposition, IBlockData iblockdata) {
        int i = blockposition.getX() & 15;
        int j = blockposition.getY();
        int k = blockposition.getZ() & 15;

        int i1 = k << 4 | i;

        if (j >= getChunkFIntArray(that, i1) - 1) {
            setChunkFIntArray(that, i1, -999);
        }

        int j1 = that.heightMap[i1];
        IBlockData iblockdata1 = that.getBlockData(blockposition);
        int k1 = that.c(blockposition);

        if (iblockdata1 == iblockdata) {
            return false;
        } else {
            Block block = iblockdata.getBlock();
            Block block1 = iblockdata1.getBlock();
            ChunkSection chunksection = that.getSections()[j >> 4];
            boolean flag = false;

            if (chunksection == null) {
                if (block == Blocks.AIR) {
                    return false;
                }

                chunksection = that.getSections()[j >> 4] = new ChunkSection(j >> 4 << 4, !that.world.worldProvider.o());
                flag = j >= j1;
            }

            // CraftBukkit start - Delay removing containers until after they're cleaned up
            if (!(block1 instanceof IContainer)) {
                chunksection.setType(i, j & 15, k, iblockdata);
            }
            // CraftBukkit end

            if (block1 != block) {
                if (!that.world.isClientSide) {
                    block1.remove(that.world, blockposition, iblockdata1);
                } else if (block1 instanceof IContainer) {
                    that.world.t(blockposition);
                }
            }

            // CraftBukkit start - Remove containers now after cleanup
            if (block1 instanceof IContainer) {
                chunksection.setType(i, j & 15, k, iblockdata);
            }
            // CraftBukkit end

            if (chunksection.b(i, j & 15, k) != block) {
                return false;
            } else {
                chunksection.setType(i, j & 15, k, iblockdata);
                if (flag) {
                    that.initLighting();
                }
                TileEntity tileentity;

                if (block1 instanceof IContainer) {
                    tileentity = that.a(blockposition, Chunk.EnumTileEntityState.CHECK);
                    if(tileentity != null) {
                        tileentity.E();
                    }
                }

                // CraftBukkit - Don't place while processing the BlockPlaceEvent, unless it's a BlockContainer
                if (!that.world.isClientSide && block1 != block
                        && (!that.world.captureBlockStates || (block instanceof BlockContainer))) {
                    block.onPlace(that.world, blockposition, iblockdata);
                }

                if (block instanceof IContainer) {
                    // CraftBukkit start - Don't create tile entity if placement failed
                    if (that.getType(blockposition) != block) {
                        return false;
                    }
                    // CraftBukkit end

                    tileentity = that.a(blockposition, Chunk.EnumTileEntityState.CHECK);
                    if (tileentity == null) {
                        tileentity = ((IContainer) block).a(that.world, block.toLegacyData(iblockdata));
                        that.world.setTileEntity(blockposition, tileentity);
                    }

                    if (tileentity != null) {
                        tileentity.E();
                    }
                }

                that.e();
                return true;
            }
        }
    }

	@Override
	public void forceBlockLightLevel(World world, int x, int y, int z, int level) {
		net.minecraft.server.v1_8_R3.World w = ((CraftWorld) world).getHandle();
		w.a(EnumSkyBlock.BLOCK, new BlockPosition(x, y, z), level);
	}

	@Override
	public int getBlockLightEmission(int blockId) {
		return Block.getById(blockId).r();
	}

	@Override
	public int getBlockLightBlocking(int blockId) {
		return Block.getById(blockId).p();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void queueChunkForUpdate(Player player, int cx, int cz) {
		((CraftPlayer) player).getHandle().chunkCoordIntPairQueue.add(new ChunkCoordIntPair(cx, cz));
	}

	@Override
	public Vector[] getBlockHitbox(org.bukkit.block.Block block) {
        BlockPosition blockposition = new BlockPosition(block.getX(), block.getY(), block.getZ());
		net.minecraft.server.v1_8_R3.World w = ((CraftWorld)block.getWorld()).getHandle();
		net.minecraft.server.v1_8_R3.Block b = w.getType(blockposition).getBlock();
		b.updateShape(w, blockposition);
		return new Vector[] {
				new Vector(block.getX() + b.B(), block.getY() + b.D(), block.getZ() + b.F()),
				new Vector(block.getX() + b.C(), block.getY() + b.E(), block.getZ() + b.G())
		};
	}

    @Override
    public void recalculateBlockLighting(World world, int x, int y, int z) {
        // Don't consider blocks that are completely surrounded by other non-transparent blocks
        if (!canAffectLighting(world, x, y, z)) {
            return;
        }

        int i = x & 15;
        int j = y & 255;
        int k = z & 15;

        BlockPosition blockposition = new BlockPosition(i, j, k);

        CraftChunk craftChunk = (CraftChunk)world.getChunkAt(x >> 4, z >> 4);
        Chunk nmsChunk = craftChunk.getHandle();

        int i1 = k << 4 | i;
        int maxY = nmsChunk.heightMap[i1];

        Block block = nmsChunk.getType(blockposition);
        int j2 = block.p(); // TODO: should this be getBlockLightBlocking(Block.getId(block))?

        if (j2 > 0) {
            if (j >= maxY) {
                invokeChunkDThreeIntMethod(nmsChunk, i, j + 1, k);
            }
        } else if (j == maxY - 1) {
            invokeChunkDThreeIntMethod(nmsChunk, i, j, k);
        }

        if (nmsChunk.getBrightness(EnumSkyBlock.SKY, blockposition) > 0
                || nmsChunk.getBrightness(EnumSkyBlock.BLOCK, blockposition) > 0) {
            invokeChunkDTwoIntMethod(nmsChunk, i, k);
        }

        net.minecraft.server.v1_8_R3.World w = ((CraftWorld) world).getHandle();
        w.c(EnumSkyBlock.BLOCK, blockposition);
    }

    private boolean canAffectLighting(World world, int x, int y, int z) {
        org.bukkit.block.Block base  = world.getBlockAt(x, y, z);
        org.bukkit.block.Block east  = base.getRelative(BlockFace.EAST);
        org.bukkit.block.Block west  = base.getRelative(BlockFace.WEST);
        org.bukkit.block.Block up    = base.getRelative(BlockFace.UP);
        org.bukkit.block.Block down  = base.getRelative(BlockFace.DOWN);
        org.bukkit.block.Block south = base.getRelative(BlockFace.SOUTH);
        org.bukkit.block.Block north = base.getRelative(BlockFace.NORTH);

        return east.getType().isTransparent() ||
                west.getType().isTransparent() ||
                up.getType().isTransparent() ||
                down.getType().isTransparent() ||
                south.getType().isTransparent() ||
                north.getType().isTransparent();
    }
}
