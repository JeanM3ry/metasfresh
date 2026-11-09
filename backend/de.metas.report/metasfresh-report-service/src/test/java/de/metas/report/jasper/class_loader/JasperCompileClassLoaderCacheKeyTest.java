package de.metas.report.jasper.class_loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A compiled .jasper file is a serialized JasperReport, which another JasperReports version cannot load.
 * The cache key must therefore change with the JasperReports version, so that entries compiled before an upgrade
 * are recompiled instead of failing at load time.
 */
class JasperCompileClassLoaderCacheKeyTest
{
	@Test
	void cacheKey_differsFromTheContentOnlyHashWrittenByPreviousVersions(@TempDir final Path dir) throws Exception
	{
		final File jrxml = writeJrxml(dir, "report.jrxml", "<jasperReport name=\"report\"/>");

		// Before this change, the hash file only held the content hash, so every existing entry gets recompiled once.
		assertThat(JasperCompileClassLoader.computeCacheKey(jrxml))
				.isNotEqualTo(JasperCompileClassLoader.hashFileContent(jrxml))
				.startsWith(JasperCompileClassLoader.hashFileContent(jrxml));
	}

	@Test
	void cacheKey_containsTheRuntimeJasperReportsVersion(@TempDir final Path dir) throws Exception
	{
		final File jrxml = writeJrxml(dir, "report.jrxml", "<jasperReport name=\"report\"/>");

		assertThat(JasperCompileClassLoader.JASPERREPORTS_VERSION).isNotBlank();
		assertThat(JasperCompileClassLoader.computeCacheKey(jrxml)).endsWith("@" + JasperCompileClassLoader.JASPERREPORTS_VERSION);
	}

	@Test
	void cacheKey_isStableForTheSameContent_andChangesWithIt(@TempDir final Path dir) throws Exception
	{
		final File jrxml = writeJrxml(dir, "a.jrxml", "<jasperReport name=\"a\"/>");
		final File sameContent = writeJrxml(dir, "b.jrxml", "<jasperReport name=\"a\"/>");
		final File otherContent = writeJrxml(dir, "c.jrxml", "<jasperReport name=\"c\"/>");

		assertThat(JasperCompileClassLoader.computeCacheKey(jrxml)).isEqualTo(JasperCompileClassLoader.computeCacheKey(sameContent));
		assertThat(JasperCompileClassLoader.computeCacheKey(jrxml)).isNotEqualTo(JasperCompileClassLoader.computeCacheKey(otherContent));
	}

	private static File writeJrxml(final Path dir, final String name, final String content) throws Exception
	{
		final Path file = dir.resolve(name);
		Files.write(file, content.getBytes(StandardCharsets.UTF_8));
		return file.toFile();
	}
}
