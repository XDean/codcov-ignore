package xdean.jex.internal.codecov;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.reactivex.Observable;

public class CodecovIgnoreHandler {
  private static final String END = "#end";
  private static final String IGNORE = "ignore:";
  private static final String GENERATED = "#generated";

  private static Charset charset = Charset.forName("UTF-8");

  public static void main(String[] args) {
    updateCodecovIgnore();
  }

  public static void setCharset(Charset charset) {
    CodecovIgnoreHandler.charset = charset;
  }

  public static void updateCodecovIgnore() {
    Path codecov = Paths.get("codecov.yml");
    if (!Files.exists(codecov)) {
      System.err.println("Can't find codecov.yml");
      return;
    }
    Path path = Paths.get("src", "main", "java");
    updateCodecovIgnore(codecov, path);
  }

  public static void updateCodecovIgnore(Path codecov, Path sourcePath) {
    travese(sourcePath)
        .filter(p -> !Files.isDirectory(p))
        .filter(p -> p.getFileName().toString().endsWith(".java"))
        .filter(p -> {
          String name = StreamSupport.stream(p.spliterator(), false)
              .skip(3)
              .map(Path::toString)
              .collect(Collectors.joining("."));
          String clzName = name.substring(0, name.length() - 5);
          try {
            return Class.forName(clzName).isAnnotationPresent(CodecovIgnore.class);
          } catch (ClassNotFoundException e) {
            return false;
          }
        })
        .doOnNext(p -> System.out.println("Find file to ignore: " + p))
        .toList()
        .subscribe(ignores -> writeIgnore(codecov, ignores));
  }

  private static Observable<Path> travese(Path p) {
    return Observable.unsafeCreate(e -> {
      if (Files.isDirectory(p)) {
        try {
          Files.newDirectoryStream(p).forEach(child -> travese(child).forEach(e::onNext));
        } catch (IOException e1) {
          e.onError(e1);
        }
      } else {
        e.onNext(p);
      }
      e.onComplete();
    });
  }

  private static void writeIgnore(Path codecov, List<Path> ignores) throws IOException {
    List<String> lines = Files.readAllLines(codecov, charset);
    List<String> ignoreLines = ignores.stream()
        .map(p -> p.toString().replace('\\', '/'))
        .map(s -> "  - \"" + s + "\"")
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
      if (generateLine == -1 ^ endLine == -1) {
        System.err.println("'#generated' and '#end' tags are not synchronized, correct the file manually.");
        return;
      } else if (generateLine == -1) {
        lines.addAll(ignoreLine + 1, ignoreLines);
      } else {
        for (int i = generateLine; i <= endLine; i++) {
          lines.remove(generateLine);
        }
        lines.addAll(generateLine, ignoreLines);
      }
    }
    Files.write(codecov, lines);
    System.out.println("codecov.yml has been updated!");
  }
}
