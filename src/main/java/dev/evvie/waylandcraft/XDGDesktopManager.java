package dev.evvie.waylandcraft;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.lwjgl.system.jni.JNINativeInterface;

public class XDGDesktopManager {
	
	private HashMap<String, String> nameCache = new HashMap<String, String>();
	private HashMap<String, IconData> iconCache = new HashMap<String, IconData>();
	
	public String getName(String appID) {
		if(appID == null) return null;
		
		if(nameCache.containsKey(appID)) {
			return nameCache.get(appID);
		}
		
		String name = retrieveName(appID);
		nameCache.put(appID, name);
		return name;
	}
	
	public IconData getIcon(String appID) {
		if(appID == null) return null;
		
		if(iconCache.containsKey(appID)) {
			return iconCache.get(appID);
		}
		
		IconData icon = tryRetrieveIcon(appID);
		iconCache.put(appID, icon);
		return icon;
	}
	
	private String retrieveName(String appID) {
		File entry = findDesktopEntry(appID);
		System.out.println("Found desktop entry file: " + entry);
		if(entry == null) return null;
		
		try {
			return parseDesktopEntry(entry).get("Name");
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private IconData tryRetrieveIcon(String appID) {
		try {
			return retrieveIcon(appID);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private IconData retrieveIcon(String appID) throws IOException {
		File entry = findDesktopEntry(appID);
		System.out.println("Found desktop entry file: " + entry);
		if(entry == null) return null;
		
		String iconEntry = parseDesktopEntry(entry).get("Icon");
		System.out.println("Found icon entry: " + iconEntry);
		if(iconEntry == null) return null;
		
		File iconFile = findIconFile(iconEntry);
		System.out.println("Found icon file: " + iconFile);
		if(iconFile == null) return null;
		
		BufferedImage image = ImageIO.read(iconFile);
		int width = image.getWidth();
		int height = image.getHeight();
		int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
		
		ByteBuffer buf = ByteBuffer.allocateDirect(pixels.length * 4);
		buf.order(ByteOrder.LITTLE_ENDIAN);
		for(int pixel : pixels) {
			buf.putInt(pixel);
	    }
		buf.flip();
		
		BufferTexture texture = new BufferTexture.ShmBufferTexture(JNINativeInterface.GetDirectBufferAddress(buf), width, height, BufferTexture.FORMAT_ARGB8888);
		
		return new IconData(iconEntry, iconFile, texture);
	}
	
	private File findIconFile(String iconEntry) throws IOException {
		File absoluteIconFile = new File(iconEntry);
		if(absoluteIconFile.isAbsolute() && absoluteIconFile.exists() && absoluteIconFile.isFile()) return absoluteIconFile;
		
		File[] iconDirs = findIconDirectories();
		for(File iconDir : iconDirs) {
			File[] iconFiles = FileUtils.listFiles(iconDir, new NameFileFilter(iconEntry + ".png"), DirectoryFileFilter.DIRECTORY).toArray(File[]::new);
			
			// TODO: Don't just use the first icon that comes up
			if(iconFiles.length > 0) return iconFiles[0];
		}
		
		return null;
	}
	
	public HashMap<String, String> parseDesktopEntry(File entryFile) throws IOException {
		FileInputStream in = new FileInputStream(entryFile);
		String entryData = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		String[] entries = entryData.split("\\R", -1);
		
		int line;
		boolean foundGroupHeader = false;
		for(line = 0; line < entries.length; line++) {
			String entry = entries[line];
			if(entry.startsWith("#")) continue; // Comments
			if(entry.equals("[Desktop Entry]")) {
				foundGroupHeader = true;
				break;
			}
		}
		
		if(!foundGroupHeader) throw new IOException("Invalid desktop file! Does not contain [Desktop Entry] group header.");
		
		HashMap<String, String> entryMap = new HashMap<String, String>();
		for(line += 1; line < entries.length; line++) {
			String entry = entries[line];
			if(entry.isEmpty()) continue;
			if(entry.startsWith("#")) continue; // Comments
			if(entry.startsWith("[")) break; // End of the Desktop Entry group
			
			int equalsIdx = entry.indexOf('=');
			if(equalsIdx < 0) continue;
			
			String key = entry.substring(0, equalsIdx);
			String value = entry.substring(equalsIdx + 1);
			
			if(key.endsWith(" ")) key = key.substring(0, key.length() - 1);
			if(value.startsWith(" ")) key = key.substring(1);
			
			entryMap.put(key, value);
		}
		
		return entryMap;
	}
	
	public File findDesktopEntry(String appID) {
		if(appID.length() > 255) return null;
		if(!appID.matches("^([a-zA-Z_-][a-zA-Z0-9-_]*)(\\.[a-zA-Z_-][a-zA-Z0-9-_]*)*$")) return null;
		
		// NOTE: I think appIDs with hyphens could be interpreted as being in sub-dirs of the applications dir. Not implementing that.
		// NOTE: Technically speaking applications could have desktop entries without the .desktop extension. Not implementing that either.
		
		File[] appDirs = findAppDirectories();
		
		for(File appDir : appDirs) {
			File entry = new File(appDir, appID + ".desktop");
			if(entry.exists() && entry.isFile()) return entry;
		}
		
		return null;
	}
	
	private File[] findAppDirectories() {
		return Arrays.stream(findDataDirectories()).map((f) -> new File(f, "applications")).toArray(File[]::new);
	}
	
	// Find possible directory locations for icons, in order of how they should be processed
	private File[] findIconDirectories() {
		ArrayList<File> iconDirs = new ArrayList<File>();
		
		// For backwards compatibility add $HOME/.icons
		String home = System.getenv("HOME");
		if(home != null && !home.isEmpty()) {
			iconDirs.add(new File(home, ".icons"));
		}
		
		File[] dataDirs = findDataDirectories();
		
		// Add all of the $XDG_DATA_DIRS/icons/hicolor to prefer the default theme if possible
		List<File> xdgIcons = Arrays.stream(dataDirs)
				.map((f) -> new File(f, "icons/hicolor"))
				.toList();
		iconDirs.addAll(xdgIcons);
		
		// Add the /usr/share/pixmaps directory
		iconDirs.add(new File("/usr/share/pixmaps"));
		
		return iconDirs
				.stream()
				.filter((f) -> f.exists() && f.isDirectory())
				.toArray(File[]::new);
	}
	
	private File[] findDataDirectories() {
		String dataDirsStr = System.getenv("XDG_DATA_DIRS");
		if(dataDirsStr == null || dataDirsStr.isEmpty()) {
			dataDirsStr = "/usr/local/share:/usr/share";
		}
		
		File[] dataDirs = Arrays.stream(dataDirsStr.split(":"))
				.filter((path) -> !path.isEmpty())
				.map((path) -> new File(path))
				.toArray(File[]::new);
		
		return dataDirs;
	}
	
	public static class IconData {
		
		// Name or absolute path of the icon is given in the desktop entry for `Icon`
		public final String name;
		
		public final File file;
		
		public final BufferTexture texture;
		
		public IconData(String name, File file, BufferTexture texture) {
			this.name = name;
			this.file = file;
			this.texture = texture;
		}
		
	}
	
}
