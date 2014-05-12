package jadx.core.dex.attributes;

import jadx.core.dex.attributes.annotations.Annotation;
import jadx.core.dex.attributes.annotations.AnnotationsList;
import jadx.core.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Storage for different attribute types:
 * 1. flags - boolean attribute (set or not)
 * 2. attribute - class instance associated for attribute type,
 * only one attached to node for unique attributes, multiple for others
 */
public final class AttributesList {

	private final Set<AttributeFlag> flags;
	private final Map<AttributeType, IAttribute> uniqAttr;
	private final List<IAttribute> attributes;
	private final int[] attrCount;

	public AttributesList() {
		flags = EnumSet.noneOf(AttributeFlag.class);
		uniqAttr = new EnumMap<AttributeType, IAttribute>(AttributeType.class);
		attributes = new LinkedList<IAttribute>();
		attrCount = new int[AttributeType.getNotUniqCount()];
	}

	// Flags

	public void add(AttributeFlag flag) {
		flags.add(flag);
	}

	public boolean contains(AttributeFlag flag) {
		return flags.contains(flag);
	}

	public void remove(AttributeFlag flag) {
		flags.remove(flag);
	}

	// Attributes

	public void add(IAttribute attr) {
		if (attr.getType().isUniq()) {
			uniqAttr.put(attr.getType(), attr);
		} else {
			addMultiAttribute(attr);
		}
	}

	private void addMultiAttribute(IAttribute attr) {
		attributes.add(attr);
		attrCount[attr.getType().ordinal()]++;
	}

	private int getMultiCountInternal(AttributeType type) {
		return attrCount[type.ordinal()];
	}

	public void addAll(AttributesList otherList) {
		flags.addAll(otherList.flags);
		uniqAttr.putAll(otherList.uniqAttr);
		for (IAttribute attr : otherList.attributes) {
			addMultiAttribute(attr);
		}
	}

	public boolean contains(AttributeType type) {
		if (type.isUniq()) {
			return uniqAttr.containsKey(type);
		} else {
			return getMultiCountInternal(type) != 0;
		}
	}

	public IAttribute get(AttributeType type) {
		if (type.isUniq()) {
			return uniqAttr.get(type);
		} else {
			if (getMultiCountInternal(type) != 0) {
				for (IAttribute attr : attributes) {
					if (attr.getType() == type) {
						return attr;
					}
				}
			}
			return null;
		}
	}

	public int getCount(AttributeType type) {
		if (type.isUniq()) {
			return uniqAttr.containsKey(type) ? 1 : 0;
		} else {
			return getMultiCountInternal(type);
		}
	}

	public Annotation getAnnotation(String cls) {
		AnnotationsList aList = (AnnotationsList) get(AttributeType.ANNOTATION_LIST);
		return aList == null ? null : aList.get(cls);
	}

	public List<IAttribute> getAll(AttributeType type) {
		assert type.notUniq();

		int count = getMultiCountInternal(type);
		if (count == 0) {
			return Collections.emptyList();
		}
		List<IAttribute> attrs = new ArrayList<IAttribute>(count);
		for (IAttribute attr : attributes) {
			if (attr.getType() == type) {
				attrs.add(attr);
			}
		}
		return attrs;
	}

	public void remove(AttributeType type) {
		if (type.isUniq()) {
			uniqAttr.remove(type);
		} else {
			for (Iterator<IAttribute> it = attributes.iterator(); it.hasNext(); ) {
				IAttribute attr = it.next();
				if (attr.getType() == type) {
					it.remove();
				}
			}
			attrCount[type.ordinal()] = 0;
		}
	}

	public void remove(IAttribute attr) {
		AttributeType type = attr.getType();
		if (type.isUniq()) {
			IAttribute a = uniqAttr.get(type);
			if (a == attr) {
				uniqAttr.remove(type);
			}
		} else {
			if (getMultiCountInternal(type) == 0) {
				return;
			}

			for (Iterator<IAttribute> it = attributes.iterator(); it.hasNext(); ) {
				IAttribute a = it.next();
				if (a == attr) {
					it.remove();
					attrCount[type.ordinal()]--;
				}
			}
		}
	}

	public void clear() {
		flags.clear();
		uniqAttr.clear();
		attributes.clear();
		Arrays.fill(attrCount, 0);
	}

	public List<String> getAttributeStrings() {
		int size = flags.size() + uniqAttr.size() + attributes.size();
		if (size == 0) {
			return Collections.emptyList();
		}
		List<String> list = new ArrayList<String>(size);
		for (AttributeFlag a : flags) {
			list.add(a.toString());
		}
		for (IAttribute a : uniqAttr.values()) {
			list.add(a.toString());
		}
		for (IAttribute a : attributes) {
			list.add(a.toString());
		}
		return list;
	}

	@Override
	public String toString() {
		List<String> list = getAttributeStrings();
		if (list.isEmpty()) {
			return "";
		}
		return "A:{" + Utils.listToString(list) + "}";
	}

}
