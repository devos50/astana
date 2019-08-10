# ASTANA
ASTANA is a string deobfuscator for Android applications using backwards program slicing. Compared to existing tools, ASTANA does not require you to know the specifications of the obfuscator being used. Currently, ASTANA supports the deobfuscation of string literals in Android applications. It does so by converting an APK file to Smali files, and by analyzing the string literals in the Smali code.

ASTANA locates string literals, and extracts deobfuscation logic by applying program slicing techniques. Program slicing is a well-defined concept in software engineering to determine a subset of a program, with respect to a variable and statement of interest. After these slices have been determined, the code is executed in a seperate process, which yields the string literal in non-obfuscated form. These strings are stored in a sqlite database, which can be found in the `temp` directory (named `strings.db`).

## Running ASTANA

To run ASTANA on a specific APK file, run the following command:

```bash
java -jar astana.jar -i application.apk
```

You can also specifify a directory as input for the `-i` flag. ASTANA will then recursively search for APK files, and sequentually deobfuscate their string literals.
