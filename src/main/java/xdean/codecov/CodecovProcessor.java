package xdean.codecov;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.google.auto.service.AutoService;

import xdean.annotation.processor.toolkit.AssertException;
import xdean.annotation.processor.toolkit.CommonUtil;
import xdean.annotation.processor.toolkit.XAbstractProcessor;
import xdean.annotation.processor.toolkit.annotation.SupportedAnnotation;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotation(CodecovIgnore.class)
@SupportedOptions({ CodecovProcessor.FILE_KEY, CodecovProcessor.CHARSET_KEY, CodecovProcessor.SOURCE_FOLDER_KEY })
public class CodecovProcessor extends XAbstractProcessor {

  public static final String FILE_KEY = "codecov.file";
  public static final String DEFAULT_FILE = "codecov.yml";

  public static final String CHARSET_KEY = "codecov.charset";
  public static final String DEFAULT_CHARSET = "UTF-8";

  public static final String SOURCE_FOLDER_KEY = "codecov.src";
  public static final String DEFAULT_SOURCE = "src/main/java";

  public static final String OUTPUT_FOLDER_KEY = "codecov.target";
  public static final String DEFAULT_OUTPUT = "target/classes";

  private static final String IGNORE = "ignore:";
  private static final String GENERATED = "#generated";
  private static final String END = "#end";

  private String module;
  private String sourceFolder;
  private String generated;
  private String end;
  private final Set<Element> annotatedElements = new HashSet<>();

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    Path root = Paths.get("").toAbsolutePath();
    debug().log("Find root path: " + root);
    Path moduleRoot = root;
    String targetRelativePath = processingEnv.getOptions().getOrDefault(OUTPUT_FOLDER_KEY, DEFAULT_OUTPUT);
    try {
      FileObject tmp = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "codecov-ignore.tmp");
      Path target = Paths.get(tmp.toUri()).getParent();
      Path targetRelative = Paths.get(targetRelativePath);
      if (target.endsWith(targetRelative)) {
        moduleRoot = target.getParent();
        while (!moduleRoot.relativize(target).equals(targetRelative)) {
          moduleRoot = moduleRoot.getParent();
        }
        moduleRoot = moduleRoot.toAbsolutePath();
        debug().log("Find module root path: " + moduleRoot);
      } else {
        warning().log("Can't find current module root with target relative path: " + targetRelativePath);
      }
    } catch (Exception e) {
      moduleRoot = root;
      warning().log("Error when get module root path, use root path by default. " + CommonUtil.getStackTraceString(e));
    }
    module = root
        .relativize(moduleRoot)
        .toString()
        .replace('\\', '/');
    sourceFolder = root
        .relativize(moduleRoot.resolve(processingEnv.getOptions().getOrDefault(SOURCE_FOLDER_KEY, DEFAULT_SOURCE)))
        .toString()
        .replace('\\', '/');
    generated = (GENERATED + " " + module).trim();
    end = (END + " " + module).trim();
  }

  @Override
  public boolean processActual(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws AssertException {
    if (roundEnv.processingOver()) {
      try {
        writeIgnore(annotatedElements);
      } catch (IOException e) {
        throw new AssertException(e);
      }
      return true;
    }
    annotatedElements.addAll(roundEnv.getElementsAnnotatedWith(CodecovIgnore.class));
    return true;
  }

  private void writeIgnore(Set<? extends Element> ignores) throws IOException {
    Path codecovPath = Paths.get(processingEnv.getOptions().getOrDefault(FILE_KEY, DEFAULT_FILE));
    if (!Files.exists(codecovPath)) {
      warning().log("codecov file not found: " + codecovPath);
      return;
    }
    List<String> lines = Files.readAllLines(codecovPath,
        Charset.forName(processingEnv.getOptions().getOrDefault(CHARSET_KEY, DEFAULT_CHARSET)));
    List<String> ignoreLines = ignores.stream()
        .flatMap(e -> e.getKind() == ElementKind.PACKAGE ? e.getEnclosedElements().stream() : Stream.of(e))
        .distinct()
        .filter(e -> e instanceof TypeElement)
        .map(e -> (TypeElement) e)
        .filter(te -> te.getNestingKind() == NestingKind.TOP_LEVEL)
        .map(te -> te.getQualifiedName().toString().replace('.', '/'))
        .sorted(Comparator.comparing(s -> s.toLowerCase()))
        .map(s -> String.format("  - \"%s/%s.java\"", sourceFolder, s))
        .collect(Collectors.toList());
    ignoreLines.add(0, generated);
    ignoreLines.add(end);
    int ignoreLine = lines.indexOf(IGNORE);
    int generateLine = lines.indexOf(generated);
    int endLine = lines.indexOf(end);
    assertThat(generateLine > ignoreLine || generateLine == -1).todo(() -> error().log("#generated must after ignore:"));
    if (ignoreLine == -1) {
      lines.add("");
      lines.add(IGNORE);
      lines.addAll(ignoreLines);
    } else {
      assertThat(!(generateLine == -1 ^ endLine == -1))
          .todo(() -> error().log("'#generated' and '#end' tags are not synchronized, correct the file manually."));
      if (generateLine == -1) {
        lines.addAll(ignoreLine + 1, ignoreLines);
      } else {
        for (int i = generateLine; i <= endLine; i++) {
          lines.remove(generateLine);
        }
        lines.addAll(generateLine, ignoreLines);
      }
    }
    Files.write(codecovPath, lines);
    debug().log("codecov.yml has been updated!");
  }
}
