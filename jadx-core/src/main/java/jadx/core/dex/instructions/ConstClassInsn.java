package jadx.core.dex.instructions;

import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.nodes.InsnNode;

public class ConstClassInsn extends InsnNode {

	private final ArgType clsType;

	public ConstClassInsn(ArgType clsType) {
		super(InsnType.CONST_CLASS, 0);
		this.clsType = clsType;
	}

	public ArgType getClsType() {
		return clsType;
	}

	@Override
	public String toString() {
		return super.toString() + " " + clsType;
	}
}
