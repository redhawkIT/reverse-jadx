package jadx.core.dex.nodes;

import jadx.core.Consts;
import jadx.core.codegen.CodeWriter;
import jadx.core.dex.attributes.AttributeType;
import jadx.core.dex.attributes.LineAttrNode;
import jadx.core.dex.attributes.SourceFileAttr;
import jadx.core.dex.attributes.annotations.Annotation;
import jadx.core.dex.info.AccessInfo;
import jadx.core.dex.info.AccessInfo.AFType;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.info.FieldInfo;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.instructions.args.LiteralArg;
import jadx.core.dex.instructions.args.PrimitiveType;
import jadx.core.dex.nodes.parser.AnnotationsParser;
import jadx.core.dex.nodes.parser.FieldValueAttr;
import jadx.core.dex.nodes.parser.StaticValuesParser;
import jadx.core.utils.Utils;
import jadx.core.utils.exceptions.DecodeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.android.dx.io.ClassData;
import com.android.dx.io.ClassData.Field;
import com.android.dx.io.ClassData.Method;
import com.android.dx.io.ClassDef;

public class ClassNode extends LineAttrNode implements ILoadable {
	private static final Logger LOG = LoggerFactory.getLogger(ClassNode.class);

	private final DexNode dex;
	private final ClassInfo clsInfo;
	private final AccessInfo accessFlags;
	private ClassInfo superClass;
	private List<ClassInfo> interfaces;
	private Map<ArgType, List<ArgType>> genericMap;

	private final List<MethodNode> methods;
	private final List<FieldNode> fields;
	private Map<Object, FieldNode> constFields = Collections.emptyMap();
	private List<ClassNode> innerClasses = Collections.emptyList();

	private CodeWriter code; // generated code

	public ClassNode(DexNode dex, ClassDef cls) throws DecodeException {
		this.dex = dex;
		this.clsInfo = ClassInfo.fromDex(dex, cls.getTypeIndex());
		try {
			if (cls.getSupertypeIndex() == DexNode.NO_INDEX) {
				this.superClass = null;
			} else {
				this.superClass = ClassInfo.fromDex(dex, cls.getSupertypeIndex());
			}
			this.interfaces = new ArrayList<ClassInfo>(cls.getInterfaces().length);
			for (short interfaceIdx : cls.getInterfaces()) {
				this.interfaces.add(ClassInfo.fromDex(dex, interfaceIdx));
			}
			if (cls.getClassDataOffset() != 0) {
				ClassData clsData = dex.readClassData(cls);
				int mthsCount = clsData.getDirectMethods().length + clsData.getVirtualMethods().length;
				int fieldsCount = clsData.getStaticFields().length + clsData.getInstanceFields().length;

				methods = new ArrayList<MethodNode>(mthsCount);
				fields = new ArrayList<FieldNode>(fieldsCount);

				for (Method mth : clsData.getDirectMethods()) {
					methods.add(new MethodNode(this, mth));
				}
				for (Method mth : clsData.getVirtualMethods()) {
					methods.add(new MethodNode(this, mth));
				}

				for (Field f : clsData.getStaticFields()) {
					fields.add(new FieldNode(this, f));
				}
				loadStaticValues(cls, fields);
				for (Field f : clsData.getInstanceFields()) {
					fields.add(new FieldNode(this, f));
				}
			} else {
				methods = Collections.emptyList();
				fields = Collections.emptyList();
			}

			loadAnnotations(cls);

			parseClassSignature();
			setFieldsTypesFromSignature();

			int sfIdx = cls.getSourceFileIndex();
			if (sfIdx != DexNode.NO_INDEX) {
				String fileName = dex.getString(sfIdx);
				if (!this.getFullName().contains(fileName.replace(".java", ""))) {
					this.getAttributes().add(new SourceFileAttr(fileName));
					LOG.debug("Class '{}' compiled from '{}'", this, fileName);
				}
			}

			// restore original access flags from dalvik annotation if present
			int accFlagsValue;
			Annotation a = getAttributes().getAnnotation(Consts.DALVIK_INNER_CLASS);
			if (a != null) {
				accFlagsValue = (Integer) a.getValues().get("accessFlags");
			} else {
				accFlagsValue = cls.getAccessFlags();
			}
			this.accessFlags = new AccessInfo(accFlagsValue, AFType.CLASS);

		} catch (Exception e) {
			throw new DecodeException("Error decode class: " + getFullName(), e);
		}
	}

	private void loadAnnotations(ClassDef cls) {
		int offset = cls.getAnnotationsOffset();
		if (offset != 0) {
			try {
				new AnnotationsParser(this).parse(offset);
			} catch (DecodeException e) {
				LOG.error("Error parsing annotations in " + this, e);
			}
		}
	}

	private void loadStaticValues(ClassDef cls, List<FieldNode> staticFields) throws DecodeException {
		for (FieldNode f : staticFields) {
			if (f.getAccessFlags().isFinal()) {
				FieldValueAttr nullValue = new FieldValueAttr(null);
				f.getAttributes().add(nullValue);
			}
		}

		int offset = cls.getStaticValuesOffset();
		if (offset != 0) {
			StaticValuesParser parser = new StaticValuesParser(dex, dex.openSection(offset));
			int count = parser.processFields(staticFields);
			constFields = new LinkedHashMap<Object, FieldNode>(count);
			for (FieldNode f : staticFields) {
				AccessInfo accFlags = f.getAccessFlags();
				if (accFlags.isStatic() && accFlags.isFinal()) {
					FieldValueAttr fv = (FieldValueAttr) f.getAttributes().get(AttributeType.FIELD_VALUE);
					if (fv != null && fv.getValue() != null) {
						if (accFlags.isPublic()) {
							dex.getConstFields().put(fv.getValue(), f);
						}
						constFields.put(fv.getValue(), f);
					}
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void parseClassSignature() {
		Annotation a = this.getAttributes().getAnnotation(Consts.DALVIK_SIGNATURE);
		if (a == null) {
			return;
		}
		String sign = Utils.mergeSignature((List<String>) a.getDefaultValue());
		// parse generic map
		int end = Utils.getGenericEnd(sign);
		if (end != -1) {
			String gen = sign.substring(1, end);
			genericMap = ArgType.parseGenericMap(gen);
			sign = sign.substring(end + 1);
		}

		// parse super class signature and interfaces
		List<ArgType> list = ArgType.parseSignatureList(sign);
		if (list != null && !list.isEmpty()) {
			try {
				ArgType st = list.remove(0);
				this.superClass = ClassInfo.fromType(st);
				int i = 0;
				for (ArgType it : list) {
					ClassInfo interf = ClassInfo.fromType(it);
					interfaces.set(i, interf);
					i++;
				}
			} catch (Throwable e) {
				LOG.warn("Can't set signatures for class: {}, sign: {}", this, sign, e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void setFieldsTypesFromSignature() {
		for (FieldNode field : fields) {
			Annotation a = field.getAttributes().getAnnotation(Consts.DALVIK_SIGNATURE);
			if (a != null) {
				String sign = Utils.mergeSignature((List<String>) a.getDefaultValue());
				ArgType gType = ArgType.parseSignature(sign);
				if (gType != null) {
					field.setType(gType);
				}
			}
		}
	}

	@Override
	public void load() throws DecodeException {
		for (MethodNode mth : getMethods()) {
			mth.load();
		}
		for (ClassNode innerCls : getInnerClasses()) {
			innerCls.load();
		}
	}

	@Override
	public void unload() {
		for (MethodNode mth : getMethods()) {
			mth.unload();
		}
		for (ClassNode innerCls : getInnerClasses()) {
			innerCls.unload();
		}
	}

	public ClassInfo getSuperClass() {
		return superClass;
	}

	public List<ClassInfo> getInterfaces() {
		return interfaces;
	}

	public Map<ArgType, List<ArgType>> getGenericMap() {
		return genericMap;
	}

	public List<MethodNode> getMethods() {
		return methods;
	}

	public List<FieldNode> getFields() {
		return fields;
	}

	public FieldNode getConstField(Object obj) {
		return getConstField(obj, true);
	}

	public FieldNode getConstField(Object obj, boolean searchGlobal) {
		ClassNode cn = this;
		FieldNode field;
		do {
			field = cn.constFields.get(obj);
		}
		while (field == null
				&& (cn.clsInfo.getParentClass() != null)
				&& (cn = dex.resolveClass(cn.clsInfo.getParentClass())) != null);

		if (field == null && searchGlobal) {
			field = dex.getConstFields().get(obj);
		}
		return field;
	}

	public FieldNode getConstFieldByLiteralArg(LiteralArg arg) {
		PrimitiveType type = arg.getType().getPrimitiveType();
		if (type == null) {
			return null;
		}
		long literal = arg.getLiteral();
		switch (type) {
			case BOOLEAN:
				return getConstField(literal == 1, false);
			case CHAR:
				return getConstField((char) literal, Math.abs(literal) > 1);
			case BYTE:
				return getConstField((byte) literal, Math.abs(literal) > 1);
			case SHORT:
				return getConstField((short) literal, Math.abs(literal) > 1);
			case INT:
				return getConstField((int) literal, Math.abs(literal) > 1);
			case LONG:
				return getConstField(literal, Math.abs(literal) > 1);
			case FLOAT:
				return getConstField(Float.intBitsToFloat((int) literal), true);
			case DOUBLE:
				return getConstField(Double.longBitsToDouble(literal), true);
		}
		return null;
	}

	public FieldNode searchFieldById(int id) {
		String name = FieldInfo.getNameById(dex, id);
		for (FieldNode f : fields) {
			if (f.getName().equals(name)) {
				return f;
			}
		}
		return null;
	}

	public FieldNode searchField(FieldInfo field) {
		return searchFieldByName(field.getName());
	}

	public FieldNode searchFieldByName(String name) {
		for (FieldNode f : fields) {
			if (f.getName().equals(name)) {
				return f;
			}
		}
		return null;
	}

	public MethodNode searchMethod(MethodInfo mth) {
		for (MethodNode m : methods) {
			if (m.getMethodInfo().equals(mth)) {
				return m;
			}
		}
		return null;
	}

	public MethodNode searchMethodByName(String shortId) {
		for (MethodNode m : methods) {
			if (m.getMethodInfo().getShortId().equals(shortId)) {
				return m;
			}
		}
		return null;
	}

	public MethodNode searchMethodById(int id) {
		return searchMethodByName(MethodInfo.fromDex(dex, id).getShortId());
	}

	public List<ClassNode> getInnerClasses() {
		return innerClasses;
	}

	public void addInnerClass(ClassNode cls) {
		if (innerClasses.isEmpty()) {
			innerClasses = new ArrayList<ClassNode>(3);
		}
		innerClasses.add(cls);
	}

	public boolean isEnum() {
		return getAccessFlags().isEnum() && getSuperClass().getFullName().equals(Consts.CLASS_ENUM);
	}

	public boolean isAnonymous() {
		return clsInfo.isInner()
				&& getShortName().startsWith(Consts.ANONYMOUS_CLASS_PREFIX)
				&& getDefaultConstructor() != null;
	}

	public MethodNode getDefaultConstructor() {
		for (MethodNode mth : methods) {
			if (mth.getAccessFlags().isConstructor()
					&& mth.getMethodInfo().isConstructor()
					&& (mth.getMethodInfo().getArgsCount() == 0
					|| (mth.getArguments(false) != null && mth.getArguments(false).isEmpty()))) {
				return mth;
			}
		}
		return null;
	}

	public AccessInfo getAccessFlags() {
		return accessFlags;
	}

	public DexNode dex() {
		return dex;
	}

	public ClassInfo getClassInfo() {
		return clsInfo;
	}

	public String getShortName() {
		return clsInfo.getShortName();
	}

	public String getFullName() {
		return clsInfo.getFullName();
	}

	public String getPackage() {
		return clsInfo.getPackage();
	}

	public String getRawName() {
		return clsInfo.getRawName();
	}

	public void setCode(CodeWriter code) {
		this.code = code;
	}

	public CodeWriter getCode() {
		return code;
	}

	@Override
	public String toString() {
		return getFullName();
	}
}
