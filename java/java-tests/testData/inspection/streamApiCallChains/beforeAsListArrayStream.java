// "Replace Arrays.asList().stream() with Arrays.stream()" "true"

import java.util.Arrays;

class AsListArrayStream {
  String max(String[] args) {
    return Arrays.asL<caret>ist(args).stream().max(String::compareTo);
  }
}