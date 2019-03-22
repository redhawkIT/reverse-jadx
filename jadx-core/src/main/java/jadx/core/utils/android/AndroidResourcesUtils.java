package jadx.core.utils.android;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.android.dx.rop.code.AccessFlags;
import jadx.core.dex.attributes.AFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.core.codegen.ClassGen;
import jadx.core.codegen.CodeWriter;
import jadx.core.deobf.NameMapper;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.info.AccessInfo;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.info.ConstStorage;
import jadx.core.dex.info.FieldInfo;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.DexNode;
import jadx.core.dex.nodes.FieldNode;
import jadx.core.dex.nodes.ProcessState;
import jadx.core.dex.nodes.RootNode;
import jadx.core.dex.nodes.parser.FieldInitAttr;
import jadx.core.xmlgen.ResourceStorage;
import jadx.core.xmlgen.entry.ResourceEntry;

/**
 * Android resources specific handlers
 */
public class AndroidResourcesUtils {
	private static final Logger LOG = LoggerFactory.getLogger(AndroidResourcesUtils.class);

	private AndroidResourcesUtils() {
	}

	@Nullable
	public static ClassNode searchAppResClass(RootNode root, ResourceStorage resStorage) {
		String appPackage = root.getAppPackage();
		String fullName = appPackage != null ? appPackage + ".R" : "R";
		ClassNode resCls = root.searchClassByName(fullName);
		if (resCls != null) {
			addResourceFields(resCls, resStorage, true);
			return resCls;
		}
		LOG.info("Can't find 'R' class in app package: {}", appPackage);
		List<ClassNode> candidates = root.searchClassByShortName("R");
		if (candidates.size() == 1) {
			resCls = candidates.get(0);
			addResourceFields(resCls, resStorage, true);
			return resCls;
		}
		if (!candidates.isEmpty()) {
			LOG.info("Found several 'R' class candidates: {}", candidates);
		}
		LOG.info("App 'R' class not found, put all resources ids to : '{}'", fullName);
		resCls = makeClass(root, fullName, resStorage);
		if (resCls == null) {
			// We are in an APK without code therefore we don't have to update an 'R' class with the resources
			return null;
		}
		addResourceFields(resCls, resStorage, false);
		return resCls;
	}

	public static boolean handleAppResField(CodeWriter code, ClassGen clsGen, ClassInfo declClass) {
		ClassInfo parentClass = declClass.getParentClass();
		if (parentClass != null && parentClass.getShortName().equals("R")) {
			clsGen.useClass(code, parentClass);
			code.add('.');
			code.add(declClass.getAlias().getShortName());
			return true;
		}
		return false;
	}

	@Nullable
	private static ClassNode makeClass(RootNode root, String clsName, ResourceStorage resStorage) {
		List<DexNode> dexNodes = root.getDexNodes();
		if (dexNodes.isEmpty()) {
			return null;
		}
		ClassNode rCls = new ClassNode(dexNodes.get(0), clsName, AccessFlags.ACC_PUBLIC | AccessFlags.ACC_FINAL);
		rCls.addAttr(AType.COMMENTS, "This class is generated by JADX");
		rCls.setState(ProcessState.PROCESSED);
		return rCls;
	}

	private static void addResourceFields(ClassNode resCls, ResourceStorage resStorage, boolean rClsExists) {
		Map<Integer, FieldNode> resFieldsMap = fillResFieldsMap(resCls);
		Map<String, ClassNode> innerClsMap = new TreeMap<>();
		if (rClsExists) {
			for (ClassNode innerClass : resCls.getInnerClasses()) {
				innerClsMap.put(innerClass.getShortName(), innerClass);
			}
		}
		for (ResourceEntry resource : resStorage.getResources()) {
			final String resTypeName = resource.getTypeName();
			ClassNode typeCls = innerClsMap.computeIfAbsent(
					resTypeName,
					name -> addClassForResType(resCls, rClsExists, name)
			);
			final String resName;
			if ("style".equals(resTypeName)) {
				resName = resource.getKeyName().replace('.', '_');
			} else {
				resName = resource.getKeyName();
			}
			FieldNode rField = typeCls.searchFieldByName(resName);
			if (rField == null) {
				FieldInfo rFieldInfo = FieldInfo.from(typeCls.dex(), typeCls.getClassInfo(), resName, ArgType.INT);
				rField = new FieldNode(typeCls, rFieldInfo, AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC | AccessFlags.ACC_FINAL);
				rField.addAttr(FieldInitAttr.constValue(resource.getId()));
				typeCls.getFields().add(rField);
				if (rClsExists) {
					rField.addAttr(AType.COMMENTS, "added by JADX");
				}
			}
			FieldNode fieldNode = resFieldsMap.get(resource.getId());
			if (fieldNode != null
					&& !fieldNode.getName().equals(resName)
					&& NameMapper.isValidIdentifier(resName)) {
				fieldNode.add(AFlag.DONT_RENAME);
				fieldNode.getFieldInfo().setAlias(resName);
			}
		}
	}

	@NotNull
	private static ClassNode addClassForResType(ClassNode resCls, boolean rClsExists, String typeName) {
		ClassNode newTypeCls = new ClassNode(resCls.dex(), resCls.getFullName() + "$" + typeName,
				AccessFlags.ACC_PUBLIC | AccessFlags.ACC_STATIC | AccessFlags.ACC_FINAL);
		resCls.addInnerClass(newTypeCls);
		if (rClsExists) {
			newTypeCls.addAttr(AType.COMMENTS, "added by JADX");
		}
		return newTypeCls;
	}

	@NotNull
	private static Map<Integer, FieldNode> fillResFieldsMap(ClassNode resCls) {
		Map<Integer, FieldNode> resFieldsMap = new HashMap<>();
		ConstStorage constStorage = resCls.root().getConstValues();
		Map<Object, FieldNode> constFields = constStorage.getGlobalConstFields();
		for (Map.Entry<Object, FieldNode> entry : constFields.entrySet()) {
			Object key = entry.getKey();
			FieldNode field = entry.getValue();
			AccessInfo accessFlags = field.getAccessFlags();
			if (field.getType().equals(ArgType.INT)
					&& accessFlags.isStatic()
					&& accessFlags.isFinal()
					&& key instanceof Integer) {
				resFieldsMap.put((Integer) key, field);
			}
		}
		return resFieldsMap;
	}
}
