package jadx.gui.utils;

import jadx.core.dex.info.AccessInfo;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.utils.exceptions.JadxRuntimeException;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.net.URL;

public class Utils {

	private static final ImageIcon ICON_STATIC = Utils.openIcon("static_co");
	private static final ImageIcon ICON_FINAL = Utils.openIcon("final_co");
	private static final ImageIcon ICON_ABSTRACT = Utils.openIcon("abstract_co");
	private static final ImageIcon ICON_NATIVE = Utils.openIcon("native_co");

	public static ImageIcon openIcon(String name) {
		String iconPath = "/icons-16/" + name + ".png";
		URL resource = Utils.class.getResource(iconPath);
		if (resource == null) {
			throw new JadxRuntimeException("Icon not found: " + iconPath);
		}
		return new ImageIcon(resource);
	}

	public static void addKeyBinding(JComponent comp, KeyStroke key, String id, AbstractAction action) {
		comp.getInputMap().put(key, id);
		comp.getActionMap().put(id, action);
	}

	public static String typeFormat(String name, ArgType type) {
		return "<html>" + name + "<span style='color:#888888;'> : " + typeStr(type) + "</span></html>";
	}

	public static String typeStr(ArgType type) {
		if (type.isObject()) {
			String cls = type.getObject();
			int dot = cls.lastIndexOf('.');
			if (dot != -1) {
				return cls.substring(dot + 1);
			} else {
				return cls;
			}
		}
		if (type.isArray()) {
			return typeStr(type.getArrayElement()) + "[]";
		}
		return type.toString();
	}

	public static OverlayIcon makeIcon(AccessInfo af, Icon pub, Icon pri, Icon pro, Icon def) {
		Icon icon;
		if (af.isPublic()) {
			icon = pub;
		} else if (af.isPrivate()) {
			icon = pri;
		} else if (af.isProtected()) {
			icon = pro;
		} else {
			icon = def;
		}
		OverlayIcon overIcon = new OverlayIcon(icon);
		if (af.isFinal()) overIcon.add(ICON_FINAL);
		if (af.isStatic()) overIcon.add(ICON_STATIC);
		if (af.isAbstract()) overIcon.add(ICON_ABSTRACT);
		if (af.isNative()) overIcon.add(ICON_NATIVE);
		return overIcon;
	}
}
