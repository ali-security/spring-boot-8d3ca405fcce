/*
 * Copyright 2012-2020 the original author or authors.
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

package org.springframework.boot.web.server;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;

import org.junit.Assume;
import org.junit.Test;

import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AbstractConfigurableWebServerFactory}.
 */
public class AbstractConfigurableWebServerFactoryTests {

	@Test
	public void createTempDirCreatesNewEmptyDirectoryEachTime() {
		AbstractConfigurableWebServerFactory factory = new TestWebServerFactory(8080);
		File first = factory.createTempDir("tomcat");
		File second = factory.createTempDir("tomcat");
		try {
			assertThat(first).isDirectory();
			assertThat(second).isDirectory();
			assertThat(first).isNotEqualTo(second);
			assertThat(first.list()).isEmpty();
			assertThat(second.list()).isEmpty();
			assertThat(first.getName()).startsWith("tomcat.").contains("8080");
			assertThat(Files.isSymbolicLink(first.toPath())).isFalse();
		}
		finally {
			FileSystemUtils.deleteRecursively(first);
			FileSystemUtils.deleteRecursively(second);
		}
	}

	@Test
	public void createTempDirIsOnlyAccessibleByOwner() throws IOException {
		assumePosix();
		File tempDir = new TestWebServerFactory(8080).createTempDir("tomcat");
		Path ownerReference = Files.createTempFile("tomcat-owner", ".tmp");
		try {
			Path path = tempDir.toPath();
			assertThat(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS))
					.isEqualTo(EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
							PosixFilePermission.OWNER_EXECUTE));
			assertThat(Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))
					.isEqualTo(Files.getOwner(ownerReference, LinkOption.NOFOLLOW_LINKS));
		}
		finally {
			FileSystemUtils.deleteRecursively(tempDir);
			Files.deleteIfExists(ownerReference);
		}
	}

	private void assumePosix() {
		Assume.assumeTrue("POSIX file attributes not supported",
				FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
	}

	static class TestWebServerFactory extends AbstractConfigurableWebServerFactory {

		TestWebServerFactory(int port) {
			super(port);
		}

	}

}
