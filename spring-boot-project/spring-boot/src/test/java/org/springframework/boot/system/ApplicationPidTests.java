/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.system;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.springframework.util.FileCopyUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.contentOf;

/**
 * Tests for {@link ApplicationPid}.
 *
 * @author Phillip Webb
 */
public class ApplicationPidTests {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void toStringWithPid() {
		assertThat(new ApplicationPid("123").toString()).isEqualTo("123");
	}

	@Test
	public void toStringWithoutPid() {
		assertThat(new ApplicationPid(null).toString()).isEqualTo("???");
	}

	@Test
	public void throwIllegalStateWritingMissingPid() throws Exception {
		ApplicationPid pid = new ApplicationPid(null);
		assertThatIllegalStateException().isThrownBy(() -> pid.write(this.temporaryFolder.newFile()))
				.withMessageContaining("No PID available");
	}

	@Test
	public void writePid() throws Exception {
		ApplicationPid pid = new ApplicationPid("123");
		File file = this.temporaryFolder.newFile();
		pid.write(file);
		String actual = FileCopyUtils.copyToString(new FileReader(file));
		assertThat(actual).isEqualTo("123");
	}

	@Test
	public void writeNewPid() throws Exception {
		// gh-10784
		ApplicationPid pid = new ApplicationPid("123");
		File file = this.temporaryFolder.newFile();
		file.delete();
		pid.write(file);
		String actual = FileCopyUtils.copyToString(new FileReader(file));
		assertThat(actual).isEqualTo("123");
	}

	@Test
	public void overwriteExistingPid() throws Exception {
		File file = new File(this.temporaryFolder.getRoot(), "pid");
		new ApplicationPid("123").write(file);
		assertThat(contentOf(file)).isEqualTo("123");
		new ApplicationPid("456").write(file);
		assertThat(contentOf(file)).isEqualTo("456");
	}

	@Test
	public void overwriteLongerExistingPid() throws Exception {
		File file = new File(this.temporaryFolder.getRoot(), "pid");
		new ApplicationPid("123456").write(file);
		new ApplicationPid("789").write(file);
		assertThat(contentOf(file)).isEqualTo("789");
	}

	@Test
	public void writePidCreatesParentDirectories() throws Exception {
		File file = new File(this.temporaryFolder.getRoot(), "a/b/pid");
		new ApplicationPid("123").write(file);
		assertThat(contentOf(file)).isEqualTo("123");
	}

	@Test
	public void whenSymlinkToNonExistentTargetExistsAtPidFileLocationWriteThrows() throws IOException {
		assumePosix();
		File link = new File(this.temporaryFolder.getRoot(), "pid");
		File target = new File(this.temporaryFolder.getRoot(), "target");
		createSymbolicLink(link.toPath(), target.toPath());
		ApplicationPid pid = new ApplicationPid("123");
		assertThatIOException().isThrownBy(() -> pid.write(link));
		assertThat(Files.isSymbolicLink(link.toPath())).isTrue();
		assertThat(target).doesNotExist();
	}

	@Test
	public void whenSymlinkToTargetExistsAtPidFileLocationWriteThrows() throws IOException {
		assumePosix();
		File link = new File(this.temporaryFolder.getRoot(), "pid");
		Path target = new File(this.temporaryFolder.getRoot(), "target").toPath();
		Files.write(target, "target".getBytes(), StandardOpenOption.CREATE_NEW);
		createSymbolicLink(link.toPath(), target);
		ApplicationPid pid = new ApplicationPid("123");
		assertThatIOException().isThrownBy(() -> pid.write(link));
		assertThat(Files.isSymbolicLink(link.toPath())).isTrue();
		assertThat(contentOf(target.toFile())).isEqualTo("target");
	}

	@Test
	public void getPidFromJvm() {
		assertThat(new ApplicationPid().toString()).isNotEmpty();
	}

	private void createSymbolicLink(Path link, Path target) {
		try {
			Files.createSymbolicLink(link, target);
		}
		catch (Exception ex) {
			Assume.assumeNoException("Symlink creation not supported", ex);
		}
	}

	private void assumePosix() {
		Assume.assumeTrue("POSIX file attributes not supported",
				FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
	}

}
