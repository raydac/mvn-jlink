package com.igormaznitsa.mvnjlink.jdkproviders.providers;

import com.igormaznitsa.meta.annotation.MustNotContainNull;
import com.igormaznitsa.mvnjlink.mojos.AbstractJdkToolMojo;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.NotImplementedException;

public class MicrosoftJdkProvider extends UrlLinkJdkProvider {
  public MicrosoftJdkProvider(@Nonnull final AbstractJdkToolMojo mojo) {
    super(mojo);
  }

  @Nonnull
  @Override
  public Path getPathToJdk(@Nullable final String authorization,
                           @Nonnull final Map<String, String> config,
                           @Nonnull @MustNotContainNull Consumer<Path>... loadedArchiveConsumers
  ) throws IOException {
    throw new NotImplementedException("Not implemented yet");
  }

}
