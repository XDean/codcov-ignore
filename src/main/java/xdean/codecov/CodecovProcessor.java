package xdean.codecov;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;

import com.google.auto.service.AutoService;

import xdean.annotation.processor.toolkit.AssertException;
import xdean.annotation.processor.toolkit.XAbstractProcessor;
import xdean.annotation.processor.toolkit.annotation.SupportedAnnotation;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotation(CodecovIgnore.class)
@SupportedOptions({CodecovProcessor.FILE_KEY, CodecovProcessor.CHARSET_KEY, CodecovProcessor.SOURCE_FOLDER_KEY})
public class CodecovProcessor extends XAbstractProcessor {

  public static final String FILE_KEY = "codecov.file";
  public static final String DEFAULT_FILE = "codecov.yml";

  public static final String CHARSET_KEY = "codecov.charset";
  public static final String DEFAULT_CHARSET = "UTF-8";

  public static final String SOURCE_FOLDER_KEY = "codecov.src";
  public static final String DEFAULT_SOURCE = "src/main/java";

  private static final String IGNORE = "ignore:";
  private static final String GENERATED = "#generated";
  private static final String END = "#end";

  @Override
  public boolean processActual(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws AssertException {
    if (roundEnv.processingOver()) {
      return false;
    }
    try {
      writeIgnore(roundEnv.getElementsAnnotatedWith(CodecovIgnore.class));
    } catch (IOException e) {
      throw new AssertException(e);
    }
    return true;
  }

  private void writeIgnore(Set<? extends Element> ignores) throws IOException {
    Path codecovPath = Paths.get(processingEnv.getOptions().getOrDefault(FILE_KEY, DEFAULT_FILE));
    if (!Files.exists(codecovPath)) {
      warning().log("codecov file not found: " + codecovPath);
      return;
    }
    String sourceFolder = processingEnv.getOptions().getOrDefault(SOURCE_FOLDER_KEY, DEFAULT_SOURCE);
    List<String> lines = Files.readAllLines(codecovPath,
        Charset.forName(processingEnv.getOptions().getOrDefault(CHARSET_KEY, DEFAULT_CHARSET)));
    List<String> ignoreLines = ignores.stream()
        .filter(e -> e instanceof TypeElement)
        .map(e -> (TypeElement) e)
        .filter(te -> te.getNestingKind() == NestingKind.TOP_LEVEL)
        .map(te -> te.getQualifiedName().toString().replace('.', '/'))
        .sorted(Comparator.comparing(s -> s.toLowerCase()))
        .map(s -> String.format("  - \"%s/%s.java\"", sourceFolder, s))
        .collect(Collectors.toList());
    ignoreLines.add(0, GENERATED);
    ignoreLines.add(END);
    int ignoreLine = lines.indexOf(IGNORE);
    int generateLine = lines.indexOf(GENERATED);
    int endLine = lines.indexOf(END);
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
