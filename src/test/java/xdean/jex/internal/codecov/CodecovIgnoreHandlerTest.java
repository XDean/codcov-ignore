package xdean.jex.internal.codecov;

import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.Test;

public class CodecovIgnoreHandlerTest {
  private static final Path RESOURCES = Paths.get("src", "test", "resources");
  private static final Path CODE_PATH = RESOURCES.resolveSibling("java");

  @Test
  public void test() throws Exception {
    Path origin = RESOURCES.resolve("origin.yml");
    Path copy = Files.copy(origin, Files.createTempFile("codecov", ".yml"), StandardCopyOption.REPLACE_EXISTING);
    Path expect = RESOURCES.resolve("expect.yml");
    CodecovIgnoreHandler.updateCodecovIgnore(copy, CODE_PATH);
    List<String> resultLines = Files.readAllLines(copy);
    List<String> expectLines = Files.readAllLines(expect);
    for (int i = 0; i < resultLines.size() && i < expectLines.size(); i++) {
      assertEquals("line " + (i + 1), expectLines.get(i), resultLines.get(i));
    }
    assertEquals("line size", expectLines.size(), resultLines.size());
  }

  @Test
  public void test2() throws Exception {
    Path origin = RESOURCES.resolve("origin2.yml");
    Path copy = Files.copy(origin, Files.createTempFile("codecov", ".yml"), StandardCopyOption.REPLACE_EXISTING);
    Path expect = RESOURCES.resolve("expect2.yml");
    CodecovIgnoreHandler.updateCodecovIgnore(copy, CODE_PATH);
    List<String> resultLines = Files.readAllLines(copy);
    List<String> expectLines = Files.readAllLines(expect);
    for (int i = 0; i < resultLines.size() && i < expectLines.size(); i++) {
      assertEquals("line " + (i + 1), expectLines.get(i), resultLines.get(i));
    }
    assertEquals("line size", expectLines.size(), resultLines.size());
  }
}
