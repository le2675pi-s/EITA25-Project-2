import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Data(String CN, String OU, String O) {
  public Data(String subject) {
    this(
      get(subject, "CN"),
      get(subject, "OU"),
      get(subject, "O")
    );
  }

  private static String get(String subject, String key) {
    Pattern pattern = Pattern.compile("\\b" + key + "=([^,]+)"); // key=value
    Matcher matcher = pattern.matcher(subject);
    return matcher.find() ? matcher.group(1) : null;
  }

  @Override
  public String toString() {
    return "CN=" + CN + ", OU=" + OU + ", O=" + O;
  }
}
