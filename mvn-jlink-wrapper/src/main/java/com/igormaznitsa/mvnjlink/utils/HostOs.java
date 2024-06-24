package com.igormaznitsa.mvnjlink.utils;

import static org.apache.commons.lang3.SystemUtils.IS_OS_AIX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_FREE_BSD;
import static org.apache.commons.lang3.SystemUtils.IS_OS_HP_UX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_IRIX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_MAC;
import static org.apache.commons.lang3.SystemUtils.IS_OS_MAC_OSX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_SOLARIS;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;
import static org.apache.commons.lang3.SystemUtils.IS_OS_ZOS;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.SystemUtils;

/**
 * Enum contains list of host OSes with some extra info about them like default extension.
 *
 * @since 1.2.1
 */
public enum HostOs {
  WINDOWS("windows", "exe", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_WINDOWS;
    }
  }),
  UNIX("unix", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return SystemUtils.IS_OS_UNIX;
    }
  }),
  LINUX("linux", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_LINUX;
    }
  }),
  MAC("macos", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_MAC;
    }
  }),
  MAC_OSX("macosx", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_MAC_OSX;
    }
  }),
  AIX("aix", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_AIX;
    }
  }),
  IRIX("irix", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_IRIX;
    }
  }),
  ZOS("zos", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_ZOS;
    }
  }),
  HP_UX("hpux", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_HP_UX;
    }
  }),
  FREE_BSD("freebsd", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_FREE_BSD;
    }
  }),
  SOLARIS("solaris", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return IS_OS_SOLARIS;
    }
  }),
  UNKNOWN("", "", new Supplier<Boolean>() {
    @Override
    @Nonnull
    public Boolean get() {
      return false;
    }
  });

  private static final List<HostOs> VALUES = Arrays.stream(HostOs.values())
      .sorted(Comparator.comparingInt(Enum::ordinal)).collect(Collectors.toList());
  private final String id;
  private final Supplier<Boolean> hostChecker;
  private final String defaultExtension;

  HostOs(@Nonnull final String id, @Nonnull final String defaultExtension,
         @Nonnull final Supplier<Boolean> hostChecker) {
    this.id = id;
    this.defaultExtension = defaultExtension;
    this.hostChecker = hostChecker;
  }

  @Nonnull
  public static HostOs findHostOs() {
    return VALUES.stream().filter(x -> x != UNKNOWN)
        .filter(HostOs::isHostOs)
        .reduce((a, b) -> b)
        .orElse(UNKNOWN);
  }

  @Nonnull
  public String getId() {
    return this.id;
  }

  @Nonnull
  public static HostOs findForId(@Nonnull final String id) {
    final String normalized = id.toLowerCase(Locale.ENGLISH).trim();
    return VALUES.stream().filter(x -> x != UNKNOWN)
        .filter(x -> x.getId().equals(normalized))
        .reduce((a, b) -> b).orElse(UNKNOWN);
  }

  @Nonnull
  public static String makeAllIdAsString() {
    return VALUES.stream().filter(x -> x != UNKNOWN).map(HostOs::getId)
        .collect(Collectors.joining(","));
  }

  @Nonnull
  public String getDefaultExtension() {
    return this.defaultExtension;
  }

  public boolean isHostOs() {
    return this.hostChecker.get();
  }

  public boolean isMac() {
    return this == MAC || this == MAC_OSX;
  }
}
