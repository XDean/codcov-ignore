package xdean.codecov;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import javax.tools.JavaFileObject;

import org.junit.Test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

public class CodecovTest {
  private static final Path RESOURCES = Paths.get("src", "test", "resources");
  private static final JavaFileObject GOLDEN = getSource("CodecovTest.java");

  private static JavaFileObject getSource(String source) {
    return JavaFileObjects.forResource(CodecovTest.class.getResource(source));
  }

  @Test
  public void test() throws Exception {
    Path copy = getOriginFile("test");
    Path expect = getExpectFile("test");
    Compilation compile = Compiler.javac()
        .withProcessors(new CodecovProcessor())
        .withOptions("-Acodecov.file=" + copy.toString())
        .compile(GOLDEN);
    CompilationSubject.assertThat(compile).succeededWithoutWarnings();
    assertFileEqual(expect, copy);
  }

  public static Path getOriginFile(String name) throws IOException {
    return Files.copy(RESOURCES.resolve(name + ".yml"), Files.createTempFile("codecov", ".yml"),
        StandardCopyOption.REPLACE_EXISTING);
  }

  public static Path getExpectFile(String name) throws IOException {
    return RESOURCES.resolve(name + "-expect.yml");
  }

  public static void assertFileEqual(Path expect, Path actual) throws IOException {
    List<String> expectLines = Files.readAllLines(expect);
    List<String> actualLines = Files.readAllLines(actual);
    for (int i = 0; i < actualLines.size() && i < expectLines.size(); i++) {
      assertEquals("line " + (i + 1), expectLines.get(i), actualLines.get(i));
    }
    assertEquals("line size", expectLines.size(), actualLines.size());
  }
}
