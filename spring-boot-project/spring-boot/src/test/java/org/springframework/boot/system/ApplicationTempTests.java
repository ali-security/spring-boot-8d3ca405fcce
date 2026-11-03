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
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.EnumSet;
import java.util.Set;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link ApplicationTemp}.
 *
 * @author Phillip Webb
 */
public class ApplicationTempTests {

	@Before
	@After
	public void cleanup() {
		FileSystemUtils.deleteRecursively(new ApplicationTemp().getDir());
	}

	@Test
	public void generatesConsistentTemp() {
		ApplicationTemp t1 = new ApplicationTemp();
		ApplicationTemp t2 = new ApplicationTemp();
		assertThat(t1.getDir()).isNotNull();
		assertThat(t1.getDir()).isEqualTo(t2.getDir());
	}

	@Test
	public void differentBasedOnUserDir() {
		String userDir = System.getProperty("user.dir");
		try {
			File t1 = new ApplicationTemp().getDir();
			System.setProperty("user.dir", "abc");
			File t2 = new ApplicationTemp().getDir();
			assertThat(t1).isNotEqualTo(t2);
		}
		finally {
			System.setProperty("user.dir", userDir);
		}
	}

	@Test
	public void getSubDir() {
		ApplicationTemp temp = new ApplicationTemp();
		assertThat(temp.getDir("abc")).isEqualTo(new File(temp.getDir(), "abc"));
	}

	@Test
	public void getNestedSubDir() {
		ApplicationTemp temp = new ApplicationTemp();
		File dir = temp.getDir("abc/def");
		assertThat(dir).isEqualTo(new File(temp.getDir(), "abc/def"));
		assertThat(dir).isDirectory();
	}

	@Test
	public void posixPermissions() throws IOException {
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		FileSystem fileSystem = path.getFileSystem();
		if (fileSystem.supportedFileAttributeViews().contains("posix")) {
			assertDirectoryPermissions(path);
			assertDirectoryPermissions(temp.getDir("sub").toPath());
		}
	}

	@Test
	public void whenDirectoryExistsWithBroaderPermissionsGetDirTightensThem() throws IOException {
		assumePosix();
		Path path = new ApplicationTemp().getDir().toPath();
		FileSystemUtils.deleteRecursively(path);
		// Simulate directories left behind by an earlier version that used mkdirs()
		Path subDirectory = path.resolve("servlet-sessions");
		assertThat(subDirectory.toFile().mkdirs()).isTrue();
		setPermissions(path, "rwxr-xr-x");
		setPermissions(subDirectory, "rwxrwxrwx");
		File dir = new ApplicationTemp().getDir("servlet-sessions");
		assertThat(dir.toPath()).isEqualTo(subDirectory);
		assertDirectoryPermissions(path);
		assertDirectoryPermissions(subDirectory);
	}

	@Test
	public void whenDirectoryExistsWithWrongOwnerGetDirThrows() throws IOException {
		assumePosix();
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		ApplicationTemp otherOwner = createApplicationTempOwnedBy("other-user");
		assertThatIllegalStateException().isThrownBy(otherOwner::getDir)
				.withMessageContaining("is not owned by other-user");
		FileSystemUtils.deleteRecursively(path);
	}

	@Test
	public void whenDirectoryExistsWithWrongOwnerAndBroaderPermissionsGetDirThrowsWithoutChangingPermissions()
			throws IOException {
		assumePosix();
		Path path = new ApplicationTemp().getDir().toPath();
		setPermissions(path, "rwxrwxrwx");
		ApplicationTemp otherOwner = createApplicationTempOwnedBy("other-user");
		assertThatIllegalStateException().isThrownBy(otherOwner::getDir)
				.withMessageContaining("is not owned by other-user");
		assertThat(Files.getPosixFilePermissions(path)).isEqualTo(PosixFilePermissions.fromString("rwxrwxrwx"));
		FileSystemUtils.deleteRecursively(path);
	}

	@Test
	public void whenSubDirectoryExistsWithWrongOwnerGetDirThrows() throws IOException {
		assumePosix();
		ApplicationTemp otherOwner = createApplicationTempOwnedBy("other-user");
		Path path = new ApplicationTemp().getDir().toPath();
		Files.createDirectory(path.resolve("sub"));
		// Resolve the root directory before switching to the other owner
		ReflectionTestUtils.setField(otherOwner, "path", path);
		assertThatIllegalStateException().isThrownBy(() -> otherOwner.getDir("sub"))
				.withMessageContaining("is not owned by other-user");
		FileSystemUtils.deleteRecursively(path);
	}

	@Test
	public void whenSymlinkExistsInDirectoryLocationGetDirThrows() throws IOException {
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		FileSystemUtils.deleteRecursively(path);
		Path linkTarget = Files.createTempDirectory("application-temp-tests");
		try {
			createSymbolicLink(path, linkTarget);
			assertThatIllegalStateException().isThrownBy(new ApplicationTemp()::getDir)
					.withMessageContaining("already exists but it is not a directory");
			assertThat(linkTarget.toFile().list()).isEmpty();
		}
		finally {
			Files.deleteIfExists(path);
			FileSystemUtils.deleteRecursively(linkTarget);
		}
	}

	@Test
	public void whenSymlinkExistsInSubDirectoryLocationGetDirThrows() throws IOException {
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		Path linkTarget = Files.createTempDirectory("application-temp-tests");
		try {
			createSymbolicLink(path.resolve("servlet-sessions"), linkTarget);
			assertThatIllegalStateException().isThrownBy(() -> new ApplicationTemp().getDir("servlet-sessions"))
					.withMessageContaining("already exists but it is not a directory");
		}
		finally {
			FileSystemUtils.deleteRecursively(path);
			FileSystemUtils.deleteRecursively(linkTarget);
		}
	}

	@Test
	public void whenFileExistsInDirectoryLocationGetDirThrows() throws IOException {
		ApplicationTemp temp = new ApplicationTemp();
		Path path = temp.getDir().toPath();
		FileSystemUtils.deleteRecursively(path);
		Files.createFile(path);
		try {
			assertThatIllegalStateException().isThrownBy(new ApplicationTemp()::getDir)
					.withMessageContaining("already exists but it is not a directory");
		}
		finally {
			Files.deleteIfExists(path);
		}
	}

	private ApplicationTemp createApplicationTempOwnedBy(String name) {
		ApplicationTemp temp = new ApplicationTemp();
		ReflectionTestUtils.setField(temp, "directoryOwner", new UserPrincipal() {

			@Override
			public String getName() {
				return name;
			}

		});
		return temp;
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

	private void setPermissions(Path path, String permissions) throws IOException {
		Files.getFileAttributeView(path, PosixFileAttributeView.class)
				.setPermissions(PosixFilePermissions.fromString(permissions));
	}

	private void assertDirectoryPermissions(Path path) throws IOException {
		Set<PosixFilePermission> permissions = Files.getFileAttributeView(path, PosixFileAttributeView.class)
				.readAttributes().permissions();
		assertThat(permissions).isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
				PosixFilePermission.OWNER_EXECUTE));
	}

}
