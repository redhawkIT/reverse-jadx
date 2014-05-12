package jadx.core.dex.visitors;

import jadx.core.dex.attributes.AttributeType;
import jadx.core.dex.instructions.IfNode;
import jadx.core.dex.instructions.InsnType;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.instructions.args.NamedArg;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.trycatch.CatchAttr;
import jadx.core.dex.trycatch.ExcHandlerAttr;
import jadx.core.dex.trycatch.ExceptionHandler;
import jadx.core.dex.trycatch.TryCatchBlock;
import jadx.core.utils.BlockUtils;
import jadx.core.utils.InstructionRemover;

import java.util.List;

public class BlockProcessingHelper {

	public static void visit(MethodNode mth) {
		if (mth.isNoCode()) {
			return;
		}
		for (BlockNode block : mth.getBasicBlocks()) {
			markExceptionHandlers(block);
		}
		for (BlockNode block : mth.getBasicBlocks()) {
			block.updateCleanSuccessors();
			initBlocksInIfNodes(block);
		}
		for (BlockNode block : mth.getBasicBlocks()) {
			processExceptionHandlers(mth, block);
		}
		for (BlockNode block : mth.getBasicBlocks()) {
			processTryCatchBlocks(mth, block);
		}
	}

	/**
	 * Set exception handler attribute for whole block
	 */
	private static void markExceptionHandlers(BlockNode block) {
		if (!block.getInstructions().isEmpty()) {
			InsnNode me = block.getInstructions().get(0);
			ExcHandlerAttr handlerAttr = (ExcHandlerAttr) me.getAttributes().get(AttributeType.EXC_HANDLER);
			if (handlerAttr != null && me.getType() == InsnType.MOVE_EXCEPTION) {
				ExceptionHandler excHandler = handlerAttr.getHandler();
				assert me.getOffset() == excHandler.getHandleOffset();
				// set correct type for 'move-exception' operation
				RegisterArg resArg = me.getResult();
				NamedArg excArg = (NamedArg) me.getArg(0);
				ArgType type;
				if (excHandler.isCatchAll()) {
					type = ArgType.THROWABLE;
					excArg.setName("th");
				} else {
					type = excHandler.getCatchType().getType();
					excArg.setName("e");
				}
				resArg.forceType(type);
				excArg.setType(type);

				excHandler.setArg(excArg);
				block.getAttributes().add(handlerAttr);
			}
		}
	}

	private static void processExceptionHandlers(MethodNode mth, BlockNode block) {
		ExcHandlerAttr handlerAttr = (ExcHandlerAttr) block.getAttributes().get(AttributeType.EXC_HANDLER);
		if (handlerAttr != null) {
			ExceptionHandler excHandler = handlerAttr.getHandler();
			excHandler.addBlock(block);
			for (BlockNode node : BlockUtils.collectBlocksDominatedBy(block, block)) {
				excHandler.addBlock(node);
			}
			for (BlockNode excBlock : excHandler.getBlocks()) {
				// remove 'monitor-exit' from exception handler blocks
				InstructionRemover remover = new InstructionRemover(mth, excBlock);
				for (InsnNode insn : excBlock.getInstructions()) {
					if (insn.getType() == InsnType.MONITOR_ENTER) {
						break;
					}
					if (insn.getType() == InsnType.MONITOR_EXIT) {
						remover.add(insn);
					}
				}
				remover.perform();

				// if 'throw' in exception handler block have 'catch' - merge these catch blocks
				for (InsnNode insn : excBlock.getInstructions()) {
					if (insn.getType() == InsnType.THROW) {
						CatchAttr catchAttr = (CatchAttr) insn.getAttributes().get(AttributeType.CATCH_BLOCK);
						if (catchAttr != null) {
							TryCatchBlock handlerBlock = handlerAttr.getTryBlock();
							TryCatchBlock catchBlock = catchAttr.getTryBlock();
							if (handlerBlock != catchBlock) { // TODO: why it can be?
								handlerBlock.merge(mth, catchBlock);
								catchBlock.removeInsn(insn);
							}
						}
					}
				}
			}
		}
	}

	private static void processTryCatchBlocks(MethodNode mth, BlockNode block) {
		// if all instructions in block have same 'catch' attribute mark it as 'TryCatch' block
		CatchAttr commonCatchAttr = null;
		for (InsnNode insn : block.getInstructions()) {
			CatchAttr catchAttr = (CatchAttr) insn.getAttributes().get(AttributeType.CATCH_BLOCK);
			if (catchAttr == null) {
				continue;
			}
			if (commonCatchAttr == null) {
				commonCatchAttr = catchAttr;
			} else if (commonCatchAttr != catchAttr) {
				commonCatchAttr = null;
				break;
			}
		}
		if (commonCatchAttr != null) {
			block.getAttributes().add(commonCatchAttr);
			// connect handler to block
			for (ExceptionHandler handler : commonCatchAttr.getTryBlock().getHandlers()) {
				connectHandler(mth, handler);
			}
		}
	}

	private static void connectHandler(MethodNode mth, ExceptionHandler handler) {
		int addr = handler.getHandleOffset();
		for (BlockNode block : mth.getBasicBlocks()) {
			ExcHandlerAttr bh = (ExcHandlerAttr) block.getAttributes().get(AttributeType.EXC_HANDLER);
			if (bh != null && bh.getHandler().getHandleOffset() == addr) {
				handler.setHandleBlock(block);
				break;
			}
		}
	}

	/**
	 * Init 'then' and 'else' blocks for 'if' instruction.
	 */
	private static void initBlocksInIfNodes(BlockNode block) {
		List<InsnNode> instructions = block.getInstructions();
		if (instructions.size() == 1) {
			InsnNode insn = instructions.get(0);
			if (insn.getType() == InsnType.IF) {
				((IfNode) insn).initBlocks(block);
			}
		}
	}
}
