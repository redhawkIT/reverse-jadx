package jadx.core.xmlgen;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.PrintWriter;

import java.lang.reflect.Field;

import java.util.HashMap;
import java.util.Map;

import android.R.style;
//import android.content.res.Resources;

public class BinaryXMLParser {
	private byte[] bytes;
	private String[] strings;
	private int count;
	private String nsPrefix="ERROR";
	private String nsURI="ERROR";
	private String currentTag="ERROR";
	private int numtabs=-1;
	private boolean wasOneLiner=false;
	PrintWriter writer;
	Map<Integer, String> styleMap = null;
	public BinaryXMLParser(String xmlfilepath, String xmloutfilepath) {
		//System.out.println(xmlfilepath);
		try {
			writer = new PrintWriter(xmloutfilepath,"UTF-8");
		} catch(FileNotFoundException fnfe) { die("FNFE"); }
		catch(UnsupportedEncodingException uee) { die("UEE"); }
		if(null==writer) die("null==writer");
		writer.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
		File manifest = new File(xmlfilepath);
		if(null==manifest) die("null==manifest");
		bytes = new byte[(int) manifest.length()];
		try {
			InputStream is = null;
			try {
				is = new BufferedInputStream(new FileInputStream(manifest));
				int total = 0;
				while(total < bytes.length) {
					int remain = bytes.length - total;
					int read = is.read(bytes, total, remain);
					if(read > 0) total += read;
				}
			} finally {
				is.close();
			}
		} catch(FileNotFoundException fnfe) { die("FILE NOT FOUND"); }
		catch(IOException ioe) { die("IOE"); }
		count=0;
		styleMap = new HashMap<Integer, String>();
		if(null==styleMap) die("null==styleMap");
		for(Field f : android.R.style.class.getFields()) {
			try {
				styleMap.put(f.getInt(f.getType()),f.getName());
			} catch(IllegalAccessException iae) {
				die("IAE");
			}
		}
		
	}

	public void parse() {
		if(cInt16(bytes, count) != 0x0003) die("Version is not 3");
		if(cInt16(bytes, count) != 0x0008) die("Size of header is not 8");
		if(cInt32(bytes, count) != bytes.length) die("Size of manifest doesn't match");
		while((count+2)<=bytes.length) {
			int type = cInt16(bytes, count);
			if(type==0x0001) parseStringPool();
			else if(type==0x0180) parseResourceMap();
			else if(type==0x0100) parseNameSpace();
			else if(type==0x0101) parseNameSpaceEnd();
			else if(type==0x0102) parseElement();
			else if(type==0x0103) parseElementEnd();
			else if(type==0x0000) continue; // NullType is just doing nothing
			else die("Type: " + Integer.toHexString(type) + " not yet implemented");
			//System.out.println("COUNT: "+Integer.toHexString(count));
		}
		writer.close();
		//die("Done");
	}

	private void parseStringPool() {
		if(cInt16(bytes, count) != 0x001c) die("Header header size not 28");
		int hsize = cInt32(bytes, count);
		int stringCount = cInt32(bytes, count);
		int styleCount = cInt32(bytes, count);
		int flags = cInt32(bytes, count);
		int stringsStart = cInt32(bytes, count);
		int stylesStart = cInt32(bytes, count);
/*
		System.out.println(hsize);
		System.out.println(stringCount);
		System.out.println(styleCount);
		System.out.println(flags);
		System.out.println(stringsStart);
		System.out.println(stylesStart);
*/
		int[] stringsOffsets = new int[stringCount];
		for(int i=0; i<stringCount; i++) {
			stringsOffsets[i] = cInt32(bytes, count);
			//System.out.println("i["+i+"]: " + stringsOffsets[i]);
		}
		strings = new String[stringCount];
		for(int i=0; i<stringCount; i++) {
			int off = 8 + stringsStart + stringsOffsets[i];
			int strlen = cInt16(bytes, off);
			//System.out.println("strlen: " + strlen);
			byte[] str = new byte[strlen*2];
			System.arraycopy(bytes, count, str, 0, strlen*2);
			count+=strlen*2;
			strings[i] = new String(str, Charset.forName("UTF-16LE"));
			//System.out.println("index i["+i+"] string: " + strings[i]);
			count+=2;
		}
	}

	private void parseResourceMap() {
		if(cInt16(bytes, count) != 0x8) die("Header size of resmap is not 8!");
		int rhsize = cInt32(bytes, count);
		//System.out.println("RHeader Size: " + rhsize);
		int[] ids = new int[(rhsize-8)/4];
		for(int i=0; i<ids.length; i++) {
			ids[i]=cInt32(bytes, count);
			//System.out.println("i["+i+"] ID: "+ids[i]);
			//System.out.println("Hex: 0x0" + Integer.toHexString(ids[i]) + " should be: " + strings[i]);
		}
	}

	private void parseNameSpace() {
		if(cInt16(bytes, count) != 0x0010) die("NAMESPACE header is not 0x0010");
		if(cInt32(bytes, count) != 0x18) die("NAMESPACE header chunk is not 0x18 big");
		int beginLineNumber = cInt32(bytes, count);
		//if(beginLineNumber!=2) die("NAMESPACE beginning line number != 2 not supported yet");
		//System.out.println("NAMESPACE BEGIN Line: " + beginLineNumber);
		int comment = cInt32(bytes, count);
		//System.out.println("Comment: 0x" + Integer.toHexString(comment));
		int beginPrefix = cInt32(bytes, count);
		//System.out.println("Prefix: " + strings[beginPrefix]);
		nsPrefix = strings[beginPrefix];
		int beginURI = cInt32(bytes, count);
		//System.out.println("URI: " + strings[beginURI]);
		nsURI=strings[beginURI];
		//System.out.println("COUNT: "+Integer.toHexString(count));
	}

	private void parseNameSpaceEnd() {
		if(cInt16(bytes, count) != 0x0010) die("NAMESPACE header is not 0x0010");
		if(cInt32(bytes, count) != 0x18) die("NAMESPACE header chunk is not 0x18 big");
		int endLineNumber = cInt32(bytes, count);
		//if(endLineNumber!=2) die("NAMESPACE begining line number != 2 not supported yet");
		//System.out.println("NAMESPACE END Line: " + endLineNumber);
		int comment = cInt32(bytes, count);
		//System.out.println("Comment: 0x" + Integer.toHexString(comment));
		int endPrefix = cInt32(bytes, count);
		//System.out.println("Prefix: " + strings[endPrefix]);
		nsPrefix = strings[endPrefix];
		int endURI = cInt32(bytes, count);
		nsURI=strings[endURI];
		//System.out.println("URI: " + strings[endURI]);
	}

	private void parseElement() {
		numtabs+=1;
		if(cInt16(bytes, count) != 0x0010) die("ELEMENT HEADER SIZE is not 0x10");
		//if(cInt32(bytes, count) != 0x0060) die("ELEMENT CHUNK SIZE is not 0x60");
		count+=4;
		int elementBegLineNumber = cInt32(bytes, count);
		//System.out.println("ELEMENT BEG Line: " + elementBegLineNumber + " of " + strings[startNSName]);
		int comment = cInt32(bytes, count);
		//System.out.println("Comment: 0x" + Integer.toHexString(comment));
		//System.out.println("COUNT: "+Integer.toHexString(count));
		int startNS = cInt32(bytes, count);
		//System.out.println("Namespace: 0x" + Integer.toHexString(startNS));
		int startNSName = cInt32(bytes, count); // actually is elementName...
		//System.out.println("Namespace name: " + strings[startNSName]);
		if(!wasOneLiner && !"ERROR".equals(currentTag) && !currentTag.equals(strings[startNSName])) {
			writer.println(">");
		}
		wasOneLiner=false;
		currentTag=strings[startNSName];
		for(int i=0; i<numtabs; i++) writer.print("\t");
		writer.print("<" + strings[startNSName]);
		int attributeStart = cInt16(bytes, count);
		if(attributeStart != 0x14) die("startNS's attributeStart is not 0x14");
		int attributeSize = cInt16(bytes, count);
		if(attributeSize != 0x14) die("startNS's attributeSize is not 0x14");
		int attributeCount = cInt16(bytes, count); 
		//System.out.println("startNS: attributeCount: " + attributeCount);
		int idIndex = cInt16(bytes, count);
		//System.out.println("startNS: idIndex: " + idIndex);
		int classIndex = cInt16(bytes, count);
		//System.out.println("startNS: classIndex: " + classIndex);
		int styleIndex = cInt16(bytes, count);
		if(styleIndex!=0) System.out.println("startNS: styleIndex: " + styleIndex);
		if("manifest".equals(strings[startNSName])) writer.print(" xmlns:\""+nsURI+"\"");
		if(attributeCount>0) writer.print(" ");
		for(int i=0; i<attributeCount; i++) {
			int attributeNS = cInt32(bytes, count);
			int attributeName = cInt32(bytes, count);
			int attributeRawValue = cInt32(bytes, count);
			int attrValSize = cInt16(bytes, count);
			//System.out.println(attrValSize);
			if(attrValSize != 0x08) die("attrValSize != 0x08 not supported");
			if(cInt8(bytes, count) != 0) die("res0 is not 0");
			int attrValDataType = cInt8(bytes, count);
			int attrValData = cInt32(bytes, count);
/*(
			System.out.println("ai["+i+"] ns: " + attributeNS);
			//if(attributeNS!=-1) System.out.println("ai["+i+"] Sns: " + strings[attributeNS]);
			System.out.println("ai["+i+"] name: " + attributeName);
			if(attributeName!=-1) System.out.println("ai["+i+"] Sns: " + strings[attributeName]);
			System.out.println("ai["+i+"] rawval: " + attributeRawValue);
			System.out.println("ai["+i+"] dt: " + attrValDataType);
			System.out.println("ai["+i+"] d: " + attrValData);
*/
			if(attributeNS != -1) writer.print(nsPrefix+":");
			writer.print(strings[attributeName] + "=\"");
			if(attrValDataType==0x3) writer.print(strings[attrValData]);
			else if(attrValDataType==0x10) writer.print(attrValData);
			else if(attrValDataType==0x12) {
				// TODO: data is always -1, FIXME
				if(attrValData==0) writer.print("false");
				else if(attrValData==1 || attrValData==-1) writer.print("true");
				else writer.print("UNKNOWN_BOOLEAN_TYPE");
			} else if(attrValDataType==0x1) {
				if(attrValData<0x7f000000) {
					//System.out.println("0x"+Integer.toHexString(attrValData));
					//System.out.println(styleMap.get(attrValData));
					//System.out.println(android.R.style.class);
					writer.print("@*");
					if(attributeNS != -1) writer.print(nsPrefix+":");
					writer.print("style/"+styleMap.get(attrValData).replaceAll("_", "."));
				} else {
					writer.print("0x" + Integer.toHexString(attrValData));
				}
			}
			else {
/*
	//System.out.println("ai["+i+"] ns: " + attributeNS);
	//if(attributeNS!=-1) System.out.println("ai["+i+"] Sns: " + strings[attributeNS]);
	//System.out.println("ai["+i+"] name: " + attributeName);
	if(attributeName!=-1) System.out.println("ai["+i+"] Sns: " + strings[attributeName]);
	System.out.println("ai["+i+"] rawval: " + attributeRawValue);
	if(attributeRawValue!=-1) System.out.println("ai["+i+"] Sns: " + strings[attributeRawValue]);
	System.out.println("ai["+i+"] dt: " + attrValDataType);
	System.out.println("ai["+i+"] d: " + attrValData);
*/
				if("configChanges".equals(strings[attributeName])) {
					if(attrValData==1152) writer.print("orientation");
					else if(attrValData==4016) writer.print("keyboard|keyboardHidden|orientation|screenLayout|uiMode");
					else if(attrValData==176) writer.print("keyboard|keyboardHidden|orientation");
					else if(attrValData==160) writer.print("keyboardHidden|orientation");
					else writer.print("UNKNOWN_DATA_"+Integer.toHexString(attrValData));
				} else {
					writer.print("UNKNOWN_DATA_TYPE_" + attrValDataType);
				}
			}
			writer.print("\"");
			if((i+1)<attributeCount) writer.print(" ");
		}
		//writer.println(">");
		//System.out.println("ELEMENT BEG Line: " + elementBegLineNumber + " of " + strings[startNSName]);
	}

	private void parseElementEnd() {
		if(cInt16(bytes, count) != 0x0010) die("ELEMENT END header is not 0x0010");
		if(cInt32(bytes, count) != 0x18) die("ELEMENT END header chunk is not 0x18 big");
		int endLineNumber = cInt32(bytes, count);
		//if(endLineNumber!=2) die("NAMESPACE beginning line number != 2 not supported yet");
		int comment = cInt32(bytes, count);
		//System.out.println("Comment: 0x" + Integer.toHexString(comment));
		int elementNS = cInt32(bytes, count);
		int elementName = cInt32(bytes, count);
		if(currentTag==strings[elementName]) {
			writer.println(" />");
			wasOneLiner=true;
		} else {
			for(int i=0; i<numtabs; i++) writer.print("\t");
			writer.print("</");
			if(elementNS != -1) writer.print(strings[elementNS]+":");
			writer.println(strings[elementName]+">");
		}
		numtabs-=1;
		//System.out.println("ELEMENT END Line: " + endLineNumber + " of " + strings[elementName]);
		// TODO: Mind linenumbers for real original file ;)
	}

	private int cInt8(byte[] bytes, int offset) {
		byte[] tmp = new byte[4];
		tmp[3]=bytes[count++];
		return ByteBuffer.wrap(tmp).getInt();
	}

	private int cInt16(byte[] bytes, int offset) {
		byte[] tmp = new byte[4];
		tmp[3]=bytes[count++];
		tmp[2]=bytes[count++];
		return ByteBuffer.wrap(tmp).getInt();
	}

	private int cInt32(byte[] bytes, int offset) {
		byte[] tmp = new byte[4];
		for(int i=0;i <4; i++) tmp[3-i]=bytes[count+i];
		count+=4;
		return ByteBuffer.wrap(tmp).getInt();
	}

	private void die(String message) {
		System.err.println(message);
		System.exit(3);
	}
}
