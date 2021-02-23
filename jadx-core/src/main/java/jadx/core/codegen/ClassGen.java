package jadx.core.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.jetbrains.annotations.Nullable;

import jadx.api.ICodeInfo;
import jadx.api.ICodeWriter;
import jadx.api.JadxArgs;
import jadx.api.plugins.input.data.AccessFlags;
import jadx.api.plugins.input.data.annotations.EncodedType;
import jadx.api.plugins.input.data.annotations.EncodedValue;
import jadx.core.Consts;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.attributes.AttrNode;
import jadx.core.dex.attributes.fldinit.FieldInitAttr;
import jadx.core.dex.attributes.nodes.EnumClassAttr;
import jadx.core.dex.attributes.nodes.EnumClassAttr.EnumField;
import jadx.core.dex.attributes.nodes.JadxError;
import jadx.core.dex.attributes.nodes.LineAttrNode;
import jadx.core.dex.attributes.nodes.SkipMethodArgsAttr;
import jadx.core.dex.info.AccessInfo;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.instructions.args.PrimitiveType;
import jadx.core.dex.instructions.mods.ConstructorInsn;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;
import jadx.core.utils.CodeGenUtils;
import jadx.core.utils.ErrorsCounter;
import jadx.core.utils.Utils;
import jadx.core.utils.exceptions.CodegenException;
import jadx.core.utils.exceptions.JadxRuntimeException;

public class ClassGen {

	private final ClassNode cls;
	private final ClassGen parentGen;
	private final AnnotationGen annotationGen;
	private final boolean fallback;
	private final boolean useImports;
	private final boolean showInconsistentCode;

	private final Set<ClassInfo> imports = new HashSet<>();
	private int clsDeclOffset;

	private boolean bodyGenStarted;

	public ClassGen(ClassNode cls, JadxArgs jadxArgs) {
		this(cls, null, jadxArgs.isUseImports(), jadxArgs.isFallbackMode(), jadxArgs.isShowInconsistentCode());
	}

	public ClassGen(ClassNode cls, ClassGen parentClsGen) {
		this(cls, parentClsGen, parentClsGen.useImports, parentClsGen.fallback, parentClsGen.showInconsistentCode);
	}

	public ClassGen(ClassNode cls, ClassGen parentClsGen, boolean useImports, boolean fallback, boolean showBadCode) {
		this.cls = cls;
		this.parentGen = parentClsGen;
		this.fallback = fallback;
		this.useImports = useImports;
		this.showInconsistentCode = showBadCode;

		this.annotationGen = new AnnotationGen(cls, this);
	}

	public ClassNode getClassNode() {
		return cls;
	}

	public ICodeInfo makeClass() throws CodegenException {
		ICodeWriter clsBody = cls.root().makeCodeWriter();
		addClassCode(clsBody);

		ICodeWriter clsCode = cls.root().makeCodeWriter();
		if (!"".equals(cls.getPackage())) {
			clsCode.add("package ").add(cls.getPackage()).add(';');
			clsCode.newLine();
		}
		int importsCount = imports.size();
		if (importsCount != 0) {
			List<ClassInfo> sortedImports = new ArrayList<>(imports);
			sortedImports.sort(Comparator.comparing(ClassInfo::getAliasFullName));
			sortedImports.forEach(classInfo -> {
				clsCode.startLine("import ");
				ClassNode classNode = cls.root().resolveClass(classInfo);
				if (classNode != null) {
					clsCode.attachAnnotation(classNode);
				}
				clsCode.add(classInfo.getAliasFullName());
				clsCode.add(';');
			});
			clsCode.newLine();
			imports.clear();
		}
		clsCode.add(clsBody);
		return clsCode.finish();
	}

	public void addClassCode(ICodeWriter code) throws CodegenException {
		if (cls.contains(AFlag.DONT_GENERATE)) {
			return;
		}
		if (Consts.DEBUG_USAGE) {
			addClassUsageInfo(code, cls);
		}
		CodeGenUtils.addComments(code, cls);
		insertDecompilationProblems(code, cls);
		addClassDeclaration(code);
		addClassBody(code);
	}

	public void addClassDeclaration(ICodeWriter clsCode) {
		AccessInfo af = cls.getAccessFlags();
		if (af.isInterface()) {
			af = af.remove(AccessFlags.ABSTRACT)
					.remove(AccessFlags.STATIC);
		} else if (af.isEnum()) {
			af = af.remove(AccessFlags.FINAL)
					.remove(AccessFlags.ABSTRACT)
					.remove(AccessFlags.STATIC);
		}

		// 'static' and 'private' modifier not allowed for top classes (not inner)
		if (!cls.getClassInfo().isInner()) {
			af = af.remove(AccessFlags.STATIC).remove(AccessFlags.PRIVATE);
		}

		annotationGen.addForClass(clsCode);
		insertRenameInfo(clsCode, cls);
		CodeGenUtils.addSourceFileInfo(clsCode, cls);
		clsCode.startLineWithNum(cls.getSourceLine()).add(af.makeString());
		if (af.isInterface()) {
			if (af.isAnnotation()) {
				clsCode.add('@');
			}
			clsCode.add("interface ");
		} else if (af.isEnum()) {
			clsCode.add("enum ");
		} else {
			clsCode.add("class ");
		}
		clsCode.attachDefinition(cls);
		clsCode.add(cls.getClassInfo().getAliasShortName());

		addGenericTypeParameters(clsCode, cls.getGenericTypeParameters(), true);
		clsCode.add(' ');

		ArgType sup = cls.getSuperClass();
		if (sup != null
				&& !sup.equals(ArgType.OBJECT)
				&& !cls.isEnum()) {
			clsCode.add("extends ");
			useClass(clsCode, sup);
			clsCode.add(' ');
		}

		if (!cls.getInterfaces().isEmpty() && !af.isAnnotation()) {
			if (cls.getAccessFlags().isInterface()) {
				clsCode.add("extends ");
			} else {
				clsCode.add("implements ");
			}
			for (Iterator<ArgType> it = cls.getInterfaces().iterator(); it.hasNext();) {
				ArgType interf = it.next();
				useClass(clsCode, interf);
				if (it.hasNext()) {
					clsCode.add(", ");
				}
			}
			if (!cls.getInterfaces().isEmpty()) {
				clsCode.add(' ');
			}
		}
	}

	public boolean addGenericTypeParameters(ICodeWriter code, List<ArgType> generics, boolean classDeclaration) {
		if (generics == null || generics.isEmpty()) {
			return false;
		}
		code.add('<');
		int i = 0;
		for (ArgType genericInfo : generics) {
			if (i != 0) {
				code.add(", ");
			}
			if (genericInfo.isGenericType()) {
				code.add(genericInfo.getObject());
			} else {
				useClass(code, genericInfo);
			}
			List<ArgType> list = genericInfo.getExtendTypes();
			if (list != null && !list.isEmpty()) {
				code.add(" extends ");
				for (Iterator<ArgType> it = list.iterator(); it.hasNext();) {
					ArgType g = it.next();
					if (g.isGenericType()) {
						code.add(g.getObject());
					} else {
						useClass(code, g);
						if (classDeclaration
								&& !cls.getClassInfo().isInner()
								&& cls.root().getArgs().isUseImports()) {
							addImport(ClassInfo.fromType(cls.root(), g));
						}
					}
					if (it.hasNext()) {
						code.add(" & ");
					}
				}
			}
			i++;
		}
		code.add('>');
		return true;
	}

	public void addClassBody(ICodeWriter clsCode) throws CodegenException {
		addClassBody(clsCode, false);
	}

	/**
	 * @param printClassName allows to print the original class name as comment (e.g. for inlined
	 *                       classes)
	 */
	public void addClassBody(ICodeWriter clsCode, boolean printClassName) throws CodegenException {
		clsCode.add('{');
		setBodyGenStarted(true);
		clsDeclOffset = clsCode.getLength();
		clsCode.incIndent();
		if (printClassName) {
			clsCode.startLine();
			clsCode.add("/* class " + cls.getFullName() + " */");
		}
		addFields(clsCode);
		addInnerClsAndMethods(clsCode);
		clsCode.decIndent();
		clsCode.startLine('}');
	}

	private void addInnerClsAndMethods(ICodeWriter clsCode) {
		Stream.of(cls.getInnerClasses(), cls.getMethods())
				.flatMap(Collection::stream)
				.filter(node -> !node.contains(AFlag.DONT_GENERATE) || fallback)
				.sorted(Comparator.comparingInt(LineAttrNode::getSourceLine))
				.forEach(node -> {
					if (node instanceof ClassNode) {
						addInnerClass(clsCode, (ClassNode) node);
					} else {
						addMethod(clsCode, (MethodNode) node);
					}
				});
	}

	private void addInnerClass(ICodeWriter code, ClassNode innerCls) {
		try {
			ClassGen inClGen = new ClassGen(innerCls, getParentGen());
			code.newLine();
			inClGen.addClassCode(code);
			imports.addAll(inClGen.getImports());
		} catch (Exception e) {
			innerCls.addError("Inner class code generation error", e);
		}
	}

	private boolean isInnerClassesPresents() {
		for (ClassNode innerCls : cls.getInnerClasses()) {
			if (!innerCls.contains(AFlag.ANONYMOUS_CLASS)) {
				return true;
			}
		}
		return false;
	}

	private void addMethod(ICodeWriter code, MethodNode mth) {
		if (code.getLength() != clsDeclOffset) {
			code.newLine();
		}
		int savedIndent = code.getIndent();
		try {
			addMethodCode(code, mth);
		} catch (Exception e) {
			if (mth.getParentClass().getTopParentClass().contains(AFlag.RESTART_CODEGEN)) {
				throw new JadxRuntimeException("Method generation error", e);
			}
			code.newLine().add("/*");
			code.newLine().addMultiLine(ErrorsCounter.error(mth, "Method generation error", e));
			Utils.appendStackTrace(code, e);
			code.newLine().add("*/");
			code.setIndent(savedIndent);
			mth.addError("Method generation error: " + e.getMessage(), e);
		}
	}

	private boolean isMethodsPresents() {
		for (MethodNode mth : cls.getMethods()) {
			if (!mth.contains(AFlag.DONT_GENERATE)) {
				return true;
			}
		}
		return false;
	}

	public void addMethodCode(ICodeWriter code, MethodNode mth) throws CodegenException {
		CodeGenUtils.addComments(code, mth);
		if (mth.isNoCode()) {
			MethodGen mthGen = new MethodGen(this, mth);
			mthGen.addDefinition(code);
			code.add(';');
		} else {
			insertDecompilationProblems(code, mth);
			boolean badCode = mth.contains(AFlag.INCONSISTENT_CODE);
			if (badCode && showInconsistentCode) {
				mth.remove(AFlag.INCONSISTENT_CODE);
				badCode = false;
			}
			MethodGen mthGen;
			if (badCode || fallback || mth.contains(AType.JADX_ERROR) || mth.getRegion() == null) {
				mthGen = MethodGen.getFallbackMethodGen(mth);
			} else {
				mthGen = new MethodGen(this, mth);
			}
			if (mthGen.addDefinition(code)) {
				code.add(' ');
			}
			code.add('{');
			code.incIndent();
			mthGen.addInstructions(code);
			code.decIndent();
			code.startLine('}');
		}
	}

	public void insertDecompilationProblems(ICodeWriter code, AttrNode node) {
		List<JadxError> errors = node.getAll(AType.JADX_ERROR);
		if (!errors.isEmpty()) {
			errors.stream().distinct().sorted().forEach(err -> {
				code.startLine("/*  JADX ERROR: ").add(err.getError());
				Throwable cause = err.getCause();
				if (cause != null) {
					code.incIndent();
					Utils.appendStackTrace(code, cause);
					code.decIndent();
				}
				code.add("*/");
			});
		}
		List<String> warns = node.getAll(AType.JADX_WARN);
		if (!warns.isEmpty()) {
			warns.stream().distinct().sorted()
					.forEach(warn -> code.startLine("/* JADX WARNING: ").addMultiLine(warn).add(" */"));
		}
	}

	private void addFields(ICodeWriter code) throws CodegenException {
		addEnumFields(code);
		for (FieldNode f : cls.getFields()) {
			addField(code, f);
		}
	}

	public void addField(ICodeWriter code, FieldNode f) {
		if (f.contains(AFlag.DONT_GENERATE)) {
			return;
		}
		if (Consts.DEBUG_USAGE) {
			addFieldUsageInfo(code, f);
		}
		CodeGenUtils.addComments(code, f);
		annotationGen.addForField(code, f);

		if (f.getFieldInfo().isRenamed()) {
			code.newLine();
			CodeGenUtils.addRenamedComment(code, f, f.getName());
		}
		code.startLine(f.getAccessFlags().makeString());
		useType(code, f.getType());
		code.add(' ');
		code.attachDefinition(f);
		code.add(f.getAlias());
		FieldInitAttr fv = f.get(AType.FIELD_INIT);
		if (fv != null) {
			code.add(" = ");
			if (fv.isConst()) {
				EncodedValue encodedValue = fv.getEncodedValue();
				if (encodedValue.getType() == EncodedType.ENCODED_NULL) {
					code.add(TypeGen.literalToString(0, f.getType(), cls, fallback));
				} else {
					annotationGen.encodeValue(cls.root(), code, encodedValue);
				}
			} else if (fv.isInsn()) {
				InsnGen insnGen = makeInsnGen(fv.getInsnMth());
				addInsnBody(insnGen, code, fv.getInsn());
			}
		}
		code.add(';');
	}

	private boolean isFieldsPresents() {
		for (FieldNode field : cls.getFields()) {
			if (!field.contains(AFlag.DONT_GENERATE)) {
				return true;
			}
		}
		return false;
	}

	private void addEnumFields(ICodeWriter code) throws CodegenException {
		EnumClassAttr enumFields = cls.get(AType.ENUM_CLASS);
		if (enumFields == null) {
			return;
		}
		InsnGen igen = null;
		for (Iterator<EnumField> it = enumFields.getFields().iterator(); it.hasNext();) {
			EnumField f = it.next();

			CodeGenUtils.addComments(code, f.getField());
			code.startLine(f.getField().getAlias());
			ConstructorInsn constrInsn = f.getConstrInsn();
			MethodNode callMth = cls.root().resolveMethod(constrInsn.getCallMth());
			int skipCount = getEnumCtrSkipArgsCount(callMth);
			if (constrInsn.getArgsCount() > skipCount) {
				if (igen == null) {
					igen = makeInsnGen(enumFields.getStaticMethod());
				}
				igen.generateMethodArguments(code, constrInsn, 0, callMth);
			}
			if (f.getCls() != null) {
				code.add(' ');
				new ClassGen(f.getCls(), this).addClassBody(code);
			}
			if (it.hasNext()) {
				code.add(',');
			}
		}
		if (isMethodsPresents() || isFieldsPresents() || isInnerClassesPresents()) {
			if (enumFields.getFields().isEmpty()) {
				code.startLine();
			}
			code.add(';');
			if (isFieldsPresents()) {
				code.startLine();
			}
		}
	}

	private int getEnumCtrSkipArgsCount(@Nullable MethodNode callMth) {
		if (callMth != null) {
			SkipMethodArgsAttr skipArgsAttr = callMth.get(AType.SKIP_MTH_ARGS);
			if (skipArgsAttr != null) {
				return skipArgsAttr.getSkipCount();
			}
		}
		return 0;
	}

	private InsnGen makeInsnGen(MethodNode mth) {
		MethodGen mthGen = new MethodGen(this, mth);
		return new InsnGen(mthGen, false);
	}

	private void addInsnBody(InsnGen insnGen, ICodeWriter code, InsnNode insn) {
		try {
			insnGen.makeInsn(insn, code, InsnGen.Flags.BODY_ONLY_NOWRAP);
		} catch (Exception e) {
			cls.addError("Failed to generate init code", e);
		}
	}

	public void useType(ICodeWriter code, ArgType type) {
		PrimitiveType stype = type.getPrimitiveType();
		if (stype == null) {
			code.add(type.toString());
		} else if (stype == PrimitiveType.OBJECT) {
			if (type.isGenericType()) {
				code.add(type.getObject());
			} else {
				useClass(code, type);
			}
		} else if (stype == PrimitiveType.ARRAY) {
			useType(code, type.getArrayElement());
			code.add("[]");
		} else {
			code.add(stype.getLongName());
		}
	}

	public void useClass(ICodeWriter code, String rawCls) {
		useClass(code, ArgType.object(rawCls));
	}

	public void useClass(ICodeWriter code, ArgType type) {
		ArgType outerType = type.getOuterType();
		if (outerType != null) {
			useClass(code, outerType);
			code.add('.');
			// import not needed, force use short name
			useClassShortName(code, type.getObject());
			return;
		}

		useClass(code, ClassInfo.fromType(cls.root(), type));
		List<ArgType> generics = type.getGenericTypes();
		if (generics != null) {
			code.add('<');
			int len = generics.size();
			for (int i = 0; i < len; i++) {
				if (i != 0) {
					code.add(", ");
				}
				ArgType gt = generics.get(i);
				ArgType wt = gt.getWildcardType();
				if (wt != null) {
					ArgType.WildcardBound bound = gt.getWildcardBound();
					code.add(bound.getStr());
					if (bound != ArgType.WildcardBound.UNBOUND) {
						useType(code, wt);
					}
				} else {
					useType(code, gt);
				}
			}
			code.add('>');
		}
	}

	private void useClassShortName(ICodeWriter code, String object) {
		ClassInfo classInfo = ClassInfo.fromName(cls.root(), object);
		ClassNode classNode = cls.root().resolveClass(classInfo);
		if (classNode != null) {
			code.attachAnnotation(classNode);
		}
		code.add(classInfo.getAliasShortName());
	}

	public void useClass(ICodeWriter code, ClassInfo classInfo) {
		ClassNode classNode = cls.root().resolveClass(classInfo);
		if (classNode != null) {
			useClass(code, classNode);
		} else {
			addClsName(code, classInfo);
		}
	}

	public void useClass(ICodeWriter code, ClassNode classNode) {
		code.attachAnnotation(classNode);
		addClsName(code, classNode.getClassInfo());
	}

	private void addClsName(ICodeWriter code, ClassInfo classInfo) {
		String clsName = useClassInternal(cls.getClassInfo(), classInfo);
		code.add(clsName);
	}

	private String useClassInternal(ClassInfo useCls, ClassInfo extClsInfo) {
		String fullName = extClsInfo.getAliasFullName();
		if (fallback || !useImports) {
			return fullName;
		}
		String shortName = extClsInfo.getAliasShortName();
		if (extClsInfo.getPackage().equals("java.lang") && extClsInfo.getParentClass() == null) {
			return shortName;
		}
		if (isClassInnerFor(useCls, extClsInfo)) {
			return shortName;
		}
		if (extClsInfo.isInner()) {
			return expandInnerClassName(useCls, extClsInfo);
		}
		if (isBothClassesInOneTopClass(useCls, extClsInfo)) {
			return shortName;
		}
		// don't add import if this class from same package
		if (extClsInfo.getPackage().equals(useCls.getPackage()) && !extClsInfo.isInner()) {
			return shortName;
		}
		if (searchCollision(cls.root(), useCls, extClsInfo)) {
			return fullName;
		}
		// ignore classes from default package
		if (extClsInfo.isDefaultPackage()) {
			return shortName;
		}
		if (extClsInfo.getAliasPkg().equals(useCls.getAliasPkg())) {
			fullName = extClsInfo.getAliasNameWithoutPackage();
		}
		for (ClassInfo importCls : getImports()) {
			if (!importCls.equals(extClsInfo)
					&& importCls.getAliasShortName().equals(shortName)) {
				if (extClsInfo.isInner()) {
					String parent = useClassInternal(useCls, extClsInfo.getParentClass());
					return parent + '.' + shortName;
				} else {
					return fullName;
				}
			}
		}
		addImport(extClsInfo);
		return shortName;
	}

	private String expandInnerClassName(ClassInfo useCls, ClassInfo extClsInfo) {
		List<ClassInfo> clsList = new ArrayList<>();
		clsList.add(extClsInfo);
		ClassInfo parentCls = extClsInfo.getParentClass();
		boolean addImport = true;
		while (parentCls != null) {
			if (parentCls == useCls || isClassInnerFor(useCls, parentCls)) {
				addImport = false;
				break;
			}
			clsList.add(parentCls);
			parentCls = parentCls.getParentClass();
		}
		Collections.reverse(clsList);
		if (addImport) {
			addImport(clsList.get(0));
		}
		return Utils.listToString(clsList, ".", ClassInfo::getAliasShortName);
	}

	private void addImport(ClassInfo classInfo) {
		if (parentGen != null) {
			parentGen.addImport(classInfo);
		} else {
			imports.add(classInfo);
		}
	}

	public Set<ClassInfo> getImports() {
		if (parentGen != null) {
			return parentGen.getImports();
		} else {
			return imports;
		}
	}

	private static boolean isBothClassesInOneTopClass(ClassInfo useCls, ClassInfo extClsInfo) {
		ClassInfo a = useCls.getTopParentClass();
		ClassInfo b = extClsInfo.getTopParentClass();
		if (a != null) {
			return a.equals(b);
		}
		// useCls - is a top class
		return useCls.equals(b);
	}

	private static boolean isClassInnerFor(ClassInfo inner, ClassInfo parent) {
		if (inner.isInner()) {
			ClassInfo p = inner.getParentClass();
			return Objects.equals(p, parent) || isClassInnerFor(p, parent);
		}
		return false;
	}

	private static boolean searchCollision(RootNode root, ClassInfo useCls, ClassInfo searchCls) {
		if (useCls == null) {
			return false;
		}
		String shortName = searchCls.getAliasShortName();
		if (useCls.getAliasShortName().equals(shortName)) {
			return true;
		}
		ClassNode classNode = root.resolveClass(useCls);
		if (classNode != null) {
			for (ClassNode inner : classNode.getInnerClasses()) {
				if (inner.getShortName().equals(shortName)
						&& !inner.getFullName().equals(searchCls.getAliasFullName())) {
					return true;
				}
			}
		}
		return searchCollision(root, useCls.getParentClass(), searchCls);
	}

	private void insertRenameInfo(ICodeWriter code, ClassNode cls) {
		ClassInfo classInfo = cls.getClassInfo();
		if (classInfo.hasAlias()) {
			CodeGenUtils.addRenamedComment(code, cls, classInfo.getType().getObject());
		}
	}

	private static void addClassUsageInfo(ICodeWriter code, ClassNode cls) {
		List<ClassNode> deps = cls.getDependencies();
		code.startLine("// deps - ").add(Integer.toString(deps.size()));
		for (ClassNode depCls : deps) {
			code.startLine("//  ").add(depCls.getClassInfo().getFullName());
		}
		List<ClassNode> useIn = cls.getUseIn();
		code.startLine("// use in - ").add(Integer.toString(useIn.size()));
		for (ClassNode useCls : useIn) {
			code.startLine("//  ").add(useCls.getClassInfo().getFullName());
		}
		List<MethodNode> useInMths = cls.getUseInMth();
		code.startLine("// use in methods - ").add(Integer.toString(useInMths.size()));
		for (MethodNode useMth : useInMths) {
			code.startLine("//  ").add(useMth.toString());
		}
	}

	static void addMthUsageInfo(ICodeWriter code, MethodNode mth) {
		List<MethodNode> useInMths = mth.getUseIn();
		code.startLine("// use in methods - ").add(Integer.toString(useInMths.size()));
		for (MethodNode useMth : useInMths) {
			code.startLine("//  ").add(useMth.toString());
		}
	}

	private static void addFieldUsageInfo(ICodeWriter code, FieldNode fieldNode) {
		List<MethodNode> useInMths = fieldNode.getUseIn();
		code.startLine("// use in methods - ").add(Integer.toString(useInMths.size()));
		for (MethodNode useMth : useInMths) {
			code.startLine("//  ").add(useMth.toString());
		}
	}

	public ClassGen getParentGen() {
		return parentGen == null ? this : parentGen;
	}

	public AnnotationGen getAnnotationGen() {
		return annotationGen;
	}

	public boolean isFallbackMode() {
		return fallback;
	}

	public boolean isBodyGenStarted() {
		return bodyGenStarted;
	}

	public void setBodyGenStarted(boolean bodyGenStarted) {
		this.bodyGenStarted = bodyGenStarted;
	}
}
