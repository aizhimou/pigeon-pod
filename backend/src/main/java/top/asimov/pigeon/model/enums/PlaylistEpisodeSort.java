package top.asimov.pigeon.model.enums;

import lombok.Getter;

@Getter
public enum PlaylistEpisodeSort {

  DEFAULT(null),
  POSITION_DESC(1);

  private final Integer value;

  PlaylistEpisodeSort(Integer value) {
    this.value = value;
  }

  public static PlaylistEpisodeSort fromValue(Integer value) {
    if (value == null) {
      return DEFAULT;
    }
    for (PlaylistEpisodeSort sort : values()) {
      if (value.equals(sort.value)) {
        return sort;
      }
    }
    return DEFAULT;
  }

  public boolean isDescendingPosition() {
    return this == POSITION_DESC;
  }
}
