package jadx.core.utils;

import jadx.core.dex.attributes.AttributeType;
import jadx.core.dex.instructions.InsnType;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.utils.exceptions.JadxRuntimeException;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockUtils {

	private BlockUtils() {
	}

	public static BlockNode getBlockByOffset(int offset, Iterable<BlockNode> casesBlocks) {
		for (BlockNode block : casesBlocks) {
			if (block.getStartOffset() == offset) {
				return block;
			}
		}
		throw new JadxRuntimeException("Can't find block by offset: "
				+ InsnUtils.formatOffset(offset)
				+ " in list " + casesBlocks);
	}

	public static BlockNode selectOther(BlockNode node, List<BlockNode> blocks) {
		List<BlockNode> list = blocks;
		if (list.size() > 2) {
			list = cleanBlockList(list);
		}
		if (list.size() != 2) {
			throw new JadxRuntimeException("Incorrect nodes count for selectOther: " + node + " in " + list);
		}
		BlockNode first = list.get(0);
		if (first != node) {
			return first;
		} else {
			return list.get(1);
		}
	}

	public static BlockNode selectOtherSafe(BlockNode node, List<BlockNode> blocks) {
		int size = blocks.size();
		if (size == 1) {
			BlockNode first = blocks.get(0);
			return first != node ? first : null;
		} else if (size == 2) {
			BlockNode first = blocks.get(0);
			return first != node ? first : blocks.get(1);
		}
		return null;
	}

	private static List<BlockNode> cleanBlockList(List<BlockNode> list) {
		List<BlockNode> ret = new ArrayList<BlockNode>(list.size());
		for (BlockNode block : list) {
			if (!block.getAttributes().contains(AttributeType.EXC_HANDLER)) {
				ret.add(block);
			}
		}
		return ret;
	}

	public static boolean isBackEdge(BlockNode from, BlockNode to) {
		if (to == null) {
			return false;
		}
		if (from.getCleanSuccessors().contains(to)) {
			return false; // already checked
		}
		return from.getSuccessors().contains(to);
	}

	/**
	 * Remove exception handlers from block nodes bitset
	 */
	public static void cleanBitSet(MethodNode mth, BitSet bs) {
		for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
			BlockNode block = mth.getBasicBlocks().get(i);
			if (block.getAttributes().contains(AttributeType.EXC_HANDLER)) {
				bs.clear(i);
			}
		}
	}

	/**
	 * Check if instruction contains in block (use == for comparison, not equals)
	 */
	public static boolean blockContains(BlockNode block, InsnNode insn) {
		for (InsnNode bi : block.getInstructions()) {
			if (bi == insn) {
				return true;
			}
		}
		return false;
	}

	public static boolean lastInsnType(BlockNode block, InsnType type) {
		List<InsnNode> insns = block.getInstructions();
		if (insns.isEmpty()) {
			return false;
		}
		InsnNode insn = insns.get(insns.size() - 1);
		return insn.getType() == type;
	}

	public static BlockNode getBlockByInsn(MethodNode mth, InsnNode insn) {
		assert insn != null;
		for (BlockNode bn : mth.getBasicBlocks()) {
			if (blockContains(bn, insn)) {
				return bn;
			}
		}
		return null;
	}

	public static BitSet blocksToBitSet(MethodNode mth, List<BlockNode> blocks) {
		BitSet bs = new BitSet(mth.getBasicBlocks().size());
		for (BlockNode block : blocks) {
			bs.set(block.getId());
		}
		return bs;
	}

	public static List<BlockNode> bitSetToBlocks(MethodNode mth, BitSet bs) {
		List<BlockNode> blocks = new ArrayList<BlockNode>(bs.cardinality());
		for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
			BlockNode block = mth.getBasicBlocks().get(i);
			blocks.add(block);
		}
		return blocks;
	}

	/**
	 * Return first successor which not exception handler and not follow loop back edge
	 */
	public static BlockNode getNextBlock(BlockNode block) {
		List<BlockNode> s = block.getCleanSuccessors();
		return s.isEmpty() ? null : s.get(0);
	}

	/**
	 * Return successor on path to 'pathEnd' block
	 */
	public static BlockNode getNextBlockToPath(BlockNode block, BlockNode pathEnd) {
		List<BlockNode> successors = block.getCleanSuccessors();
		if (successors.contains(pathEnd)) {
			return pathEnd;
		}
		Set<BlockNode> path = getAllPathsBlocks(block, pathEnd);
		for (BlockNode s : successors) {
			if (path.contains(s)) {
				return s;
			}
		}
		return null;
	}

	/**
	 * Collect blocks from all possible execution paths from 'start' to 'end'
	 */
	public static Set<BlockNode> getAllPathsBlocks(BlockNode start, BlockNode end) {
		Set<BlockNode> set = new HashSet<BlockNode>();
		set.add(start);
		if (start != end) {
			addPredcessors(set, end, start);
		}
		return set;
	}

	private static void addPredcessors(Set<BlockNode> set, BlockNode from, BlockNode until) {
		set.add(from);
		for (BlockNode pred : from.getPredecessors()) {
			if (pred != until && !set.contains(pred)) {
				addPredcessors(set, pred, until);
			}
		}
	}

	private static boolean traverseSuccessorsUntil(BlockNode from, BlockNode until, BitSet visited) {
		for (BlockNode s : from.getCleanSuccessors()) {
			if (s == until) {
				return true;
			}
			int id = s.getId();
			if (!visited.get(id)) {
				visited.set(id);
				if (until.isDominator(s)) {
					return true;
				}
				if (traverseSuccessorsUntil(s, until, visited)) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean isPathExists(BlockNode start, BlockNode end) {
		if (start == end) {
			return true;
		}
		if (end.isDominator(start)) {
			return true;
		}
		return traverseSuccessorsUntil(start, end, new BitSet());
	}

	public static boolean isOnlyOnePathExists(BlockNode start, BlockNode end) {
		if (start == end) {
			return true;
		}
		if (!end.isDominator(start)) {
			return false;
		}
		while (start.getCleanSuccessors().size() == 1) {
			start = start.getCleanSuccessors().get(0);
			if (start == end) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Search for first node which not dominated by block, starting from child
	 */
	public static BlockNode traverseWhileDominates(BlockNode block, BlockNode child) {
		for (BlockNode node : child.getCleanSuccessors()) {
			if (!node.isDominator(block)) {
				return node;
			} else {
				BlockNode out = traverseWhileDominates(block, node);
				if (out != null) {
					return out;
				}
			}
		}
		return null;
	}

	public static BlockNode getPathCrossBlockFor(MethodNode mth, BlockNode b1, BlockNode b2) {
		if (b1 == null || b2 == null) {
			return null;
		}
		BitSet b = new BitSet();
		b.or(b1.getDomFrontier());
		b.or(b2.getDomFrontier());
		b.clear(b1.getId());
		b.clear(b2.getId());
		if (b.cardinality() == 1) {
			BlockNode end = mth.getBasicBlocks().get(b.nextSetBit(0));
			if (isPathExists(b1, end) && isPathExists(b2, end)) {
				return end;
			}
		}
		return null;
	}

	/**
	 * Collect all block dominated by 'dominator', starting from 'start'
	 */
	public static List<BlockNode> collectBlocksDominatedBy(BlockNode dominator, BlockNode start) {
		List<BlockNode> result = new ArrayList<BlockNode>();
		collectWhileDominates(dominator, start, result);
		return result;
	}

	private static void collectWhileDominates(BlockNode dominator, BlockNode child, List<BlockNode> result) {
		for (BlockNode node : child.getCleanSuccessors()) {
			if (node.isDominator(dominator)) {
				result.add(node);
				collectWhileDominates(dominator, node, result);
			}
		}
	}
}
