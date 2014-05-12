package jadx.core.dex.visitors;

import jadx.core.dex.attributes.AttributeFlag;
import jadx.core.dex.attributes.AttributeType;
import jadx.core.dex.attributes.AttributesList;
import jadx.core.dex.attributes.IAttribute;
import jadx.core.dex.attributes.JumpAttribute;
import jadx.core.dex.attributes.LoopAttr;
import jadx.core.dex.instructions.IfNode;
import jadx.core.dex.instructions.InsnType;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.instructions.args.InsnArg;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.Edge;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.trycatch.CatchAttr;
import jadx.core.dex.trycatch.ExceptionHandler;
import jadx.core.dex.trycatch.SplitterBlockAttr;
import jadx.core.utils.BlockUtils;
import jadx.core.utils.EmptyBitSet;
import jadx.core.utils.exceptions.JadxRuntimeException;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlockMakerVisitor extends AbstractVisitor {

	// leave these instructions alone in block node
	private static final Set<InsnType> SEPARATE_INSNS = EnumSet.of(
			InsnType.RETURN,
			InsnType.IF,
			InsnType.SWITCH,
			InsnType.MONITOR_ENTER,
			InsnType.MONITOR_EXIT);

	private static final BitSet EMPTY_BITSET = new EmptyBitSet();

	@Override
	public void visit(MethodNode mth) {
		if (mth.isNoCode()) {
			return;
		}
		mth.initBasicBlocks();
		splitBasicBlocks(mth);
		processBlocksTree(mth);
		BlockProcessingHelper.visit(mth);
		mth.finishBasicBlocks();
	}

	private static void splitBasicBlocks(MethodNode mth) {
		InsnNode prevInsn = null;
		Map<Integer, BlockNode> blocksMap = new HashMap<Integer, BlockNode>();
		BlockNode curBlock = startNewBlock(mth, 0);
		mth.setEnterBlock(curBlock);

		// split into blocks
		for (InsnNode insn : mth.getInstructions()) {
			boolean startNew = false;
			if (prevInsn != null) {
				InsnType type = prevInsn.getType();
				if (type == InsnType.GOTO
						|| type == InsnType.THROW
						|| SEPARATE_INSNS.contains(type)) {

					if (type == InsnType.RETURN || type == InsnType.THROW) {
						mth.addExitBlock(curBlock);
					}
					BlockNode block = startNewBlock(mth, insn.getOffset());
					if (type == InsnType.MONITOR_ENTER || type == InsnType.MONITOR_EXIT) {
						connect(curBlock, block);
					}
					curBlock = block;
					startNew = true;
				} else {
					startNew = isSplitByJump(prevInsn, insn)
							|| SEPARATE_INSNS.contains(insn.getType())
							|| isDoWhile(blocksMap, curBlock, insn);
					if (startNew) {
						BlockNode block = startNewBlock(mth, insn.getOffset());
						connect(curBlock, block);
						curBlock = block;
					}
				}
			}
			// for try/catch make empty block for connect handlers
			if (insn.getAttributes().contains(AttributeFlag.TRY_ENTER)) {
				BlockNode block;
				if (insn.getOffset() != 0 && !startNew) {
					block = startNewBlock(mth, insn.getOffset());
					connect(curBlock, block);
					curBlock = block;
				}
				blocksMap.put(insn.getOffset(), curBlock);

				// add this insn in new block
				block = startNewBlock(mth, -1);
				curBlock.getAttributes().add(AttributeFlag.SYNTHETIC);
				block.getAttributes().add(new SplitterBlockAttr(curBlock));
				connect(curBlock, block);
				curBlock = block;
			} else {
				blocksMap.put(insn.getOffset(), curBlock);
			}
			curBlock.getInstructions().add(insn);
			prevInsn = insn;
		}
		// setup missing connections
		setupConnections(mth, blocksMap);
	}

	private static void setupConnections(MethodNode mth, Map<Integer, BlockNode> blocksMap) {
		for (BlockNode block : mth.getBasicBlocks()) {
			for (InsnNode insn : block.getInstructions()) {
				List<IAttribute> jumps = insn.getAttributes().getAll(AttributeType.JUMP);
				for (IAttribute attr : jumps) {
					JumpAttribute jump = (JumpAttribute) attr;
					BlockNode srcBlock = getBlock(jump.getSrc(), blocksMap);
					BlockNode thisblock = getBlock(jump.getDest(), blocksMap);
					connect(srcBlock, thisblock);
				}

				// connect exception handlers
				CatchAttr catches = (CatchAttr) insn.getAttributes().get(AttributeType.CATCH_BLOCK);
				// get synthetic block for handlers
				IAttribute spl = block.getAttributes().get(AttributeType.SPLITTER_BLOCK);
				if (catches != null && spl != null) {
					BlockNode connBlock = ((SplitterBlockAttr) spl).getBlock();
					for (ExceptionHandler h : catches.getTryBlock().getHandlers()) {
						BlockNode destBlock = getBlock(h.getHandleOffset(), blocksMap);
						// skip self loop in handler
						if (connBlock != destBlock) {
							connect(connBlock, destBlock);
						}
					}
				}
			}
		}
	}

	private static boolean isSplitByJump(InsnNode prevInsn, InsnNode currentInsn) {
		List<IAttribute> pJumps = prevInsn.getAttributes().getAll(AttributeType.JUMP);
		for (IAttribute j : pJumps) {
			JumpAttribute jump = (JumpAttribute) j;
			if (jump.getSrc() == prevInsn.getOffset()) {
				return true;
			}
		}
		List<IAttribute> cJumps = currentInsn.getAttributes().getAll(AttributeType.JUMP);
		for (IAttribute j : cJumps) {
			JumpAttribute jump = (JumpAttribute) j;
			if (jump.getDest() == currentInsn.getOffset()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isDoWhile(Map<Integer, BlockNode> blocksMap, BlockNode curBlock, InsnNode insn) {
		// split 'do-while' block (last instruction: 'if', target this block)
		if (insn.getType() == InsnType.IF) {
			IfNode ifs = (IfNode) (insn);
			BlockNode targetBlock = blocksMap.get(ifs.getTarget());
			if (targetBlock == curBlock) {
				return true;
			}
		}
		return false;
	}

	private static void processBlocksTree(MethodNode mth) {
		computeDominators(mth);
		markReturnBlocks(mth);

		int i = 0;
		while (modifyBlocksTree(mth)) {
			// revert calculations
			clearBlocksState(mth);
			// recalculate dominators tree
			computeDominators(mth);
			markReturnBlocks(mth);

			if (i++ > 100) {
				throw new AssertionError("Can't fix method cfg: " + mth);
			}
		}
		computeDominanceFrontier(mth);
		registerLoops(mth);
	}

	private static BlockNode getBlock(int offset, Map<Integer, BlockNode> blocksMap) {
		BlockNode block = blocksMap.get(offset);
		assert block != null;
		return block;
	}

	private static void connect(BlockNode from, BlockNode to) {
		if (!from.getSuccessors().contains(to)) {
			from.getSuccessors().add(to);
		}
		if (!to.getPredecessors().contains(from)) {
			to.getPredecessors().add(from);
		}
	}

	private static void removeConnection(BlockNode from, BlockNode to) {
		from.getSuccessors().remove(to);
		to.getPredecessors().remove(from);
	}

	private static BlockNode startNewBlock(MethodNode mth, int offset) {
		BlockNode block = new BlockNode(mth.getBasicBlocks().size(), offset);
		mth.getBasicBlocks().add(block);
		return block;
	}

	private static void computeDominators(MethodNode mth) {
		List<BlockNode> basicBlocks = mth.getBasicBlocks();
		int nBlocks = basicBlocks.size();
		for (int i = 0; i < nBlocks; i++) {
			BlockNode block = basicBlocks.get(i);
			block.setId(i);
			block.setDoms(new BitSet(nBlocks));
			block.getDoms().set(0, nBlocks);
		}

		BlockNode entryBlock = mth.getEnterBlock();
		entryBlock.getDoms().clear();
		entryBlock.getDoms().set(entryBlock.getId());

		BitSet dset = new BitSet(nBlocks);
		boolean changed;
		do {
			changed = false;
			for (BlockNode block : basicBlocks) {
				if (block == entryBlock) {
					continue;
				}
				BitSet d = block.getDoms();
				if (!changed) {
					dset.clear();
					dset.or(d);
				}
				for (BlockNode pred : block.getPredecessors()) {
					d.and(pred.getDoms());
				}
				d.set(block.getId());
				if (!changed && !d.equals(dset)) {
					changed = true;
				}
			}
		} while (changed);

		markLoops(mth);

		// clear self dominance
		for (BlockNode block : basicBlocks) {
			block.getDoms().clear(block.getId());
		}

		// calculate immediate dominators
		for (BlockNode block : basicBlocks) {
			if (block == entryBlock) {
				continue;
			}
			BlockNode idom;
			List<BlockNode> preds = block.getPredecessors();
			if (preds.size() == 1) {
				idom = preds.get(0);
			} else {
				BitSet bs = new BitSet(block.getDoms().length());
				bs.or(block.getDoms());
				for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
					BlockNode dom = basicBlocks.get(i);
					bs.andNot(dom.getDoms());
				}
				if (bs.cardinality() != 1) {
					throw new JadxRuntimeException("Can't find immediate dominator for block " + block
							+ " in " + bs + " preds:" + preds);
				}
				idom = basicBlocks.get(bs.nextSetBit(0));
			}
			block.setIDom(idom);
			idom.addDominatesOn(block);
		}
	}

	private static void computeDominanceFrontier(MethodNode mth) {
		for (BlockNode exit : mth.getExitBlocks()) {
			exit.setDomFrontier(EMPTY_BITSET);
		}
		for (BlockNode block : mth.getBasicBlocks()) {
			computeBlockDF(mth, block);
		}
	}

	private static void computeBlockDF(MethodNode mth, BlockNode block) {
		for (BlockNode c : block.getDominatesOn()) {
			computeBlockDF(mth, c);
		}
		BitSet domFrontier = null;
		for (BlockNode s : block.getSuccessors()) {
			if (s.getIDom() != block) {
				if (domFrontier == null) {
					domFrontier = new BitSet();
				}
				domFrontier.set(s.getId());
			}
		}
		for (BlockNode c : block.getDominatesOn()) {
			BitSet frontier = c.getDomFrontier();
			for (int p = frontier.nextSetBit(0); p >= 0; p = frontier.nextSetBit(p + 1)) {
				if (mth.getBasicBlocks().get(p).getIDom() != block) {
					if (domFrontier == null) {
						domFrontier = new BitSet();
					}
					domFrontier.set(p);
				}
			}
		}
		if (domFrontier == null || domFrontier.cardinality() == 0) {
			domFrontier = EMPTY_BITSET;
		}
		block.setDomFrontier(domFrontier);
	}

	private static void markReturnBlocks(MethodNode mth) {
		mth.getExitBlocks().clear();
		for (BlockNode block : mth.getBasicBlocks()) {
			if (BlockUtils.lastInsnType(block, InsnType.RETURN)) {
				block.getAttributes().add(AttributeFlag.RETURN);
				mth.getExitBlocks().add(block);
			}
		}
	}

	private static void markLoops(MethodNode mth) {
		for (BlockNode block : mth.getBasicBlocks()) {
			for (BlockNode succ : block.getSuccessors()) {
				// Every successor that dominates its predecessor is a header of a loop,
				// block -> succ is a back edge.
				if (block.getDoms().get(succ.getId())) {
					succ.getAttributes().add(AttributeFlag.LOOP_START);
					block.getAttributes().add(AttributeFlag.LOOP_END);

					LoopAttr loop = new LoopAttr(succ, block);
					succ.getAttributes().add(loop);
					block.getAttributes().add(loop);
				}
			}
		}
	}

	private static void registerLoops(MethodNode mth) {
		for (BlockNode block : mth.getBasicBlocks()) {
			AttributesList attributes = block.getAttributes();
			IAttribute loop = attributes.get(AttributeType.LOOP);
			if (loop != null && attributes.contains(AttributeFlag.LOOP_START)) {
				mth.registerLoop((LoopAttr) loop);
			}
		}
	}

	private static boolean modifyBlocksTree(MethodNode mth) {
		for (BlockNode block : mth.getBasicBlocks()) {
			if (block.getPredecessors().isEmpty() && block != mth.getEnterBlock()) {
				throw new JadxRuntimeException("Unreachable block: " + block);
			}

			// check loops
			List<IAttribute> loops = block.getAttributes().getAll(AttributeType.LOOP);
			if (loops.size() > 1) {
				boolean oneHeader = true;
				for (IAttribute a : loops) {
					LoopAttr loop = (LoopAttr) a;
					if (loop.getStart() != block) {
						oneHeader = false;
						break;
					}
				}
				if (oneHeader) {
					// several back edges connected to one loop header => make additional block
					BlockNode newLoopHeader = startNewBlock(mth, block.getStartOffset());
					newLoopHeader.getAttributes().add(AttributeFlag.SYNTHETIC);
					connect(newLoopHeader, block);
					for (IAttribute a : loops) {
						LoopAttr la = (LoopAttr) a;
						BlockNode node = la.getEnd();
						removeConnection(node, block);
						connect(node, newLoopHeader);
					}
					return true;
				}
			}
			// insert additional blocks if loop has several exits
			if (loops.size() == 1) {
				LoopAttr loop = (LoopAttr) loops.get(0);
				List<Edge> edges = loop.getExitEdges();
				if (edges.size() > 1) {
					boolean change = false;
					for (Edge edge : edges) {
						BlockNode target = edge.getTarget();
						if (!target.getAttributes().contains(AttributeFlag.SYNTHETIC)) {
							insertBlockBetween(mth, edge.getSource(), target);
							change = true;
						}
					}
					if (change) {
						return true;
					}
				}
			}
		}
		if (splitReturn(mth)) {
			return true;
		}
		if (mergeReturn(mth)) {
			return true;
		}
		return false;
	}

	private static BlockNode insertBlockBetween(MethodNode mth, BlockNode source, BlockNode target) {
		BlockNode newBlock = startNewBlock(mth, target.getStartOffset());
		newBlock.getAttributes().add(AttributeFlag.SYNTHETIC);
		removeConnection(source, target);
		connect(source, newBlock);
		connect(newBlock, target);
		return newBlock;
	}

	/**
	 * Merge return blocks for void methods
	 */
	private static boolean mergeReturn(MethodNode mth) {
		if (mth.getExitBlocks().size() == 1 || !mth.getReturnType().equals(ArgType.VOID)) {
			return false;
		}
		for (BlockNode exitBlock : mth.getExitBlocks()) {
			List<BlockNode> preds = exitBlock.getPredecessors();
			if (preds.size() != 1) {
				continue;
			}
			BlockNode pred = preds.get(0);
			for (BlockNode otherExitBlock : mth.getExitBlocks()) {
				if (exitBlock != otherExitBlock
						&& otherExitBlock.isDominator(pred)
						&& otherExitBlock.getPredecessors().size() == 1) {
					BlockNode otherPred = otherExitBlock.getPredecessors().get(0);
					if (pred != otherPred) {
						// merge
						removeConnection(otherPred, otherExitBlock);
						connect(otherPred, exitBlock);
						cleanExitNodes(mth);
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Splice return block if several predecessors presents
	 */
	private static boolean splitReturn(MethodNode mth) {
		if (mth.getExitBlocks().size() != 1) {
			return false;
		}
		BlockNode exitBlock = mth.getExitBlocks().get(0);
		if (exitBlock.getPredecessors().size() > 1
				&& exitBlock.getInstructions().size() == 1
				&& !exitBlock.getInstructions().get(0).getAttributes().contains(AttributeType.CATCH_BLOCK)
				&& !exitBlock.getAttributes().contains(AttributeFlag.SYNTHETIC)) {
			InsnNode returnInsn = exitBlock.getInstructions().get(0);
			List<BlockNode> preds = new ArrayList<BlockNode>(exitBlock.getPredecessors());
			if (returnInsn.getArgsCount() != 0 && !isReturnArgAssignInPred(preds, returnInsn)) {
				return false;
			}
			for (BlockNode pred : preds) {
				BlockNode newRetBlock = startNewBlock(mth, exitBlock.getStartOffset());
				newRetBlock.getAttributes().add(AttributeFlag.SYNTHETIC);
				newRetBlock.getInstructions().add(duplicateReturnInsn(returnInsn));
				removeConnection(pred, exitBlock);
				connect(pred, newRetBlock);
			}
			cleanExitNodes(mth);
			return true;
		}
		return false;
	}

	private static boolean isReturnArgAssignInPred(List<BlockNode> preds, InsnNode returnInsn) {
		RegisterArg arg = (RegisterArg) returnInsn.getArg(0);
		int regNum = arg.getRegNum();
		for (BlockNode pred : preds) {
			for (InsnNode insnNode : pred.getInstructions()) {
				RegisterArg result = insnNode.getResult();
				if (result != null && result.getRegNum() == regNum) {
					return true;
				}
			}
		}
		return false;
	}

	private static void cleanExitNodes(MethodNode mth) {
		for (Iterator<BlockNode> iterator = mth.getExitBlocks().iterator(); iterator.hasNext(); ) {
			BlockNode exitBlock = iterator.next();
			if (exitBlock.getPredecessors().isEmpty()) {
				mth.getBasicBlocks().remove(exitBlock);
				iterator.remove();
			}
		}
	}

	private static InsnNode duplicateReturnInsn(InsnNode returnInsn) {
		InsnNode insn = new InsnNode(returnInsn.getType(), returnInsn.getArgsCount());
		if (returnInsn.getArgsCount() == 1) {
			RegisterArg arg = (RegisterArg) returnInsn.getArg(0);
			insn.addArg(InsnArg.reg(arg.getRegNum(), arg.getType()));
		}
		insn.getAttributes().addAll(returnInsn.getAttributes());
		insn.setOffset(returnInsn.getOffset());
		return insn;
	}

	private static void clearBlocksState(MethodNode mth) {
		for (BlockNode block : mth.getBasicBlocks()) {
			AttributesList attrs = block.getAttributes();
			attrs.remove(AttributeType.LOOP);
			attrs.remove(AttributeFlag.LOOP_START);
			attrs.remove(AttributeFlag.LOOP_END);

			block.setDoms(null);
			block.setIDom(null);
			block.setDomFrontier(null);
			block.getDominatesOn().clear();
		}
	}
}
