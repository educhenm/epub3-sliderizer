package danielweck.epub3.sliderizer.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import danielweck.epub3.sliderizer.Epub3FileSet;
import danielweck.epub3.sliderizer.XHTML;

public abstract class Fielder {

	private final static Map<Class<? extends Fielder>, Map<String, String>> Fields = new HashMap<Class<? extends Fielder>, Map<String, String>>();

	public Map<String, String> getFields() {

		Class<? extends Fielder> clazz = this.getClass(); // .asSubclass(Fielder.class);

		if (Fields.containsKey(clazz)) {
			return Fields.get(clazz);
		}

		return null;
	}

	protected Fielder() throws Exception {

		Class<? extends Fielder> clazz = this.getClass(); // .asSubclass(Fielder.class);
		if (Fields.containsKey(clazz)) {
			return;
		}

		Map<String, String> fieldsForClass = new HashMap<String, String>();
		Fields.put(clazz, fieldsForClass);

		Field[] fields = clazz.getDeclaredFields();

		Object obj = clazz.newInstance(); // NEEDED! (!= this)

		for (Field field : fields) {
			int modifiers = field.getModifiers();
			if (field.getType() == String.class && Modifier.isPublic(modifiers)
					&& !Modifier.isStatic(modifiers)
					&& !Modifier.isFinal(modifiers)) {
				String name = field.getName();

				try {
					String value = (String) field.get(obj);

					fieldsForClass.put(name, value);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	public String toString() {
		StringBuilder stringBuilder = new StringBuilder();
		try {
			toString(stringBuilder);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return stringBuilder.toString();
	}

	public void toString(Appendable appendable) throws Exception {

		Map<String, String> fields = getFields();

		for (Map.Entry<String, String> field : fields.entrySet()) {

			String fieldName = field.getKey();
			String defaultFieldValue = field.getValue();
			String fieldValue = (String) this.getClass()
					.getDeclaredField(fieldName).get(this);
			appendable.append("!!!!!! [");
			appendable.append(fieldName);
			appendable.append(']');

			if (defaultFieldValue == null && fieldValue == null
					|| defaultFieldValue != null
					&& defaultFieldValue.equals(fieldValue)) {
				appendable.append(" (default)");
			}
			appendable.append('\n');
			appendable.append(fieldValue);

			appendable.append('\n');
		}
	}

	static final String COMMENT_PREFIX = "// ";

	protected static String nextLine(BufferedReader bufferedReader,
			int verbosity, boolean preserveWhitespace, String currentFieldName)
			throws IOException {
		// Windows CR LF "\r\n"
		// (0Dh 0Ah for DOS, 0Dh for older Macs, 0Ah for Unix/Linux)

		String nextLine = null;
		while ((nextLine = bufferedReader.readLine()) != null) {
			if (!preserveWhitespace) {
				nextLine = nextLine.trim();

				if (nextLine.length() == 0) {
					continue;
				}
			}

			if (verbosity > 0) {
				System.out.println("LINE: " + nextLine);
			}

			if (// !preserveWhitespace &&
			(currentFieldName == null || (!currentFieldName
					.equals(Slide.FIELD_JS_SCRIPT)
			// && !currentFieldName.equals(Slide.FIELD_CSS_STYLE) BECAUSE
			// COMMENT BEFORE NEXT FIELD NAME GETS INCLUDED! :(
					))
					&& nextLine.startsWith(COMMENT_PREFIX)) {
				continue;
			}

			break;
		}

		return nextLine;
	}

	protected abstract boolean parseSpecial(File file, String line,
			Stack<BufferedReader> bufferedReaders,
			Map<BufferedReader, String> mapBufferedReaderLine, int verbosity)
			throws Exception;

	static final String FIELD_PREFIX = "_";

	protected static boolean fieldEqual(CharSequence line,
			CharSequence fieldName) {
		// return line.equals(FIELD_PREFIX + fieldName);

		int offset = FIELD_PREFIX.length();

		if (fieldName.length() != (line.length() - offset)) {
			return false;
		}

		for (int i = 0; i < fieldName.length(); i++) {
			char c1 = line.charAt(i + offset);
			char c2 = fieldName.charAt(i);

			if (c1 != c2) {
				return false;
			}
		}

		return true;
	}

	protected static String FIELD_INCLUDE = "INCLUDE";

	protected static void parseFields(File file, Fielder fielder,
			Stack<BufferedReader> bufferedReaders,
			Map<BufferedReader, String> mapBufferedReaderLine, int verbosity)
			throws Exception {

		if (bufferedReaders.isEmpty()) {
			return;
		}
		BufferedReader bufferedReader = bufferedReaders.peek();

		Map<String, String> fields = fielder.getFields();

		boolean preserveWhitespace = false;

		String currentFieldName = null;
		String line = null;
		StringBuilder lines = new StringBuilder();

		while (true) {
			line = nextLine(bufferedReader, verbosity, preserveWhitespace,
					currentFieldName);

			if (line == null) {

				System.out.println(" ");
				System.out.println(">>>>> STACKED: " + bufferedReaders.size());

				BufferedReader restoredBufferedReader = bufferedReaders.pop();
				// assert restoredBufferedReader == bufferedReader

				if (!bufferedReaders.isEmpty()) {

					restoredBufferedReader = bufferedReaders.peek();
					bufferedReader.close();
					bufferedReader = restoredBufferedReader;

					String lastLine = mapBufferedReaderLine.get(bufferedReader);
					mapBufferedReaderLine.remove(bufferedReader);

					if (verbosity > 0) {
						System.out.println(" ");
						System.out
								.println(">>>>> RESTORING STACKED DATA FRAGMENT BUFFER: "
										+ lastLine);
					}

					if (lastLine == null) {
						continue;
					}

					line = lastLine;
				}
			}

			String found = null;
			boolean special = false;
			if (line != null && line.length() > 0) {

				if (fieldEqual(line, Fielder.FIELD_INCLUDE)) {
					// found = Fielder.FIELD_INCLUDE;
					StringBuilder includes = new StringBuilder();

					String lastLine = null;
					while (true) {
						line = nextLine(bufferedReader, verbosity, false,
								Fielder.FIELD_INCLUDE);

						lastLine = line;

						if (line != null && line.length() > 0) {
							boolean fff = false;
							for (Map.Entry<String, String> field : fields
									.entrySet()) {
								String fieldName = field.getKey();
								if (fieldEqual(line, fieldName)) {
									fff = true;
									break;
								}
							}
							fff = fff || line.equals(Slide.SLIDE_MARKER)
									|| fieldEqual(line, Fielder.FIELD_INCLUDE);

							if (fff)
								break;

							if (includes.length() > 0) {
								includes.append('\n');
							}
							includes.append(line);
						}

						if (line == null)
							break;
					}

					if (lastLine != null) {
						mapBufferedReaderLine.put(bufferedReader, lastLine);
					}

					ArrayList<String> array = Epub3FileSet.splitPaths(includes
							.toString());
					// for (String path : array) {
					for (int i = array.size() - 1; i >= 0; i--) {
						String path = array.get(i);

						File fileFragment = new File(file.getParentFile(), path);
						if (!fileFragment.exists()) {
							throw new FileNotFoundException(
									fileFragment.getAbsolutePath());
						}

						if (verbosity > 0) {
							System.out.println(" ");
							System.out.println(">>>>> DATA FRAGMENT: "
									+ fileFragment.getAbsolutePath());
						}

						BufferedReader bufferedReaderFragment = new BufferedReader(
								new InputStreamReader(new FileInputStream(
										fileFragment), "UTF-8")
						// new FileReader(fileFragment)
						);
						// Fielder.parseFields(fileFragment, fielder,
						// bufferedReaderFragment, verbosity);
						bufferedReaders.push(bufferedReaderFragment);
					}

					bufferedReader = bufferedReaders.peek();
					continue;

				} else {
					for (Map.Entry<String, String> field : fields.entrySet()) {
						String fieldName = field.getKey();
						if (fieldEqual(line, fieldName)) {
							found = fieldName;
							break;
						}
					}
					if (found == null) {
						special = fielder.parseSpecial(file, line,
								bufferedReaders, mapBufferedReaderLine,
								verbosity);
					}
				}
			}

			if (line == null || found != null || special) {
				preserveWhitespace = false;

				if (currentFieldName != null && lines.length() > 0) {

					fielder.getClass().getDeclaredField(currentFieldName)
							.set(fielder, lines.toString());

					lines.setLength(0);
				}

				if (line == null) {
					break;
				} else if (found != null) {
					currentFieldName = found;
					if (currentFieldName.equals(Slide.FIELD_CONTENT)
							|| currentFieldName.equals(Slide.FIELD_NOTES)
							|| currentFieldName.equals(Slide.FIELD_JS_SCRIPT)
							|| currentFieldName.equals(Slide.FIELD_CSS_STYLE)) {
						preserveWhitespace = true;
					}
				} else if (special) {
					break;
				}
			} else {
				if (currentFieldName != null
						&& (currentFieldName.equals(Slide.FIELD_CONTENT) || currentFieldName
								.equals(Slide.FIELD_NOTES))
						&& line.equals(XHTML.NOMARKDOWN)) {
					preserveWhitespace = false;
				}

				if (lines.length() > 0) {
					lines.append('\n');
				}
				if (line.length() > 0 && line.charAt(0) == '\\') {
					if (line.length() > 1) {
						lines.append(line.substring(1));
					}
				} else {
					lines.append(line);
				}
			}
		}
	}
}
