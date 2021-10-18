/*
 * Copyright 2019-2021 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dytanic.cloudnet.console.animation.progressbar.wrapper;

import de.dytanic.cloudnet.console.IConsole;
import de.dytanic.cloudnet.console.animation.progressbar.ConsoleProgressAnimation;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jetbrains.annotations.NotNull;

public final class WrappedInputStream extends FilterInputStream {

  private final ConsoleProgressAnimation animation;
  private long mark = 0;

  public WrappedInputStream(
    @NotNull InputStream in,
    @NotNull IConsole console,
    @NotNull ConsoleProgressAnimation animation
  ) {
    super(in);

    this.animation = animation;
    console.startAnimation(animation);
  }

  @Override
  public int read() throws IOException {
    int read = this.in.read();
    if (read != -1) {
      this.animation.step();
    }
    return read;
  }

  @Override
  public int read(byte[] b) throws IOException {
    int read = this.in.read(b);
    if (read != -1) {
      this.animation.stepBy(read);
    }
    return read;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    int read = this.in.read(b, off, len);
    if (read != -1) {
      this.animation.stepBy(read);
    }
    return read;
  }

  @Override
  public long skip(long n) throws IOException {
    long length = this.in.skip(n);
    this.animation.stepBy(n);
    return length;
  }

  @Override
  public synchronized void mark(int mark) {
    this.in.mark(mark);
    this.mark = this.animation.getCurrent();
  }

  @Override
  public synchronized void reset() throws IOException {
    this.in.reset();
    this.animation.stepTo(this.mark);
  }

  @Override
  public void close() throws IOException {
    this.in.close();
    this.animation.stepToEnd();
  }
}