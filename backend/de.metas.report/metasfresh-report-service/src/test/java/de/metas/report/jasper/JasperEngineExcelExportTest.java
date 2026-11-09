package de.metas.report.jasper;

import de.metas.report.jasper.exporter.MetasJRXlsExporter;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.base.JRBasePrintPage;
import net.sf.jasperreports.engine.base.JRBasePrintText;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.VerticalTextAlignEnum;
import net.sf.jasperreports.export.XlsReportConfiguration;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.crypt.CryptoFunctions;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down the XLS output of {@link JasperEngine#exportAsExcel(JasperPrint)}: sheet protection, which depends on the
 * report's {@value XlsReportConfiguration#PROPERTY_PASSWORD} property, and the columns hidden through
 * {@value MetasJRXlsExporter#PROPERTY_COLUMN_HIDDEN}.
 *
 * <p>The test builds a synthetic {@link JasperPrint} (no JRXML, no database) and reads the result with POI.
 */
public class JasperEngineExcelExportTest
{
	private static final String DEFAULT_PASSWORD = "unlock";

	/**
	 * Builds a page with two adjacent text elements, i.e. two columns. The second one is marked as hidden when requested.
	 */
	private static JasperPrint buildTestPrint(@Nullable final String passwordProperty, final boolean hideSecondColumn)
	{
		final JasperPrint print = new JasperPrint();
		print.setName("XlsExportTest");
		print.setPageWidth(595);
		print.setPageHeight(842);
		if (passwordProperty != null)
		{
			print.setProperty(XlsReportConfiguration.PROPERTY_PASSWORD, passwordProperty);
		}

		final JRBasePrintPage page = new JRBasePrintPage();
		page.addElement(makeTextElement(print, "visible", 0, false));
		page.addElement(makeTextElement(print, "hidden", 100, hideSecondColumn));
		print.addPage(page);
		return print;
	}

	private static JRBasePrintText makeTextElement(
			final JasperPrint print,
			final String text,
			final int x,
			final boolean hidden)
	{
		final JRBasePrintText element = new JRBasePrintText(print.getDefaultStyleProvider());
		element.setX(x);
		element.setY(0);
		element.setWidth(100);
		element.setHeight(20);
		element.setFontName("SansSerif");
		element.setFontSize(10f);
		element.setHorizontalTextAlign(HorizontalTextAlignEnum.LEFT);
		element.setVerticalTextAlign(VerticalTextAlignEnum.TOP);
		element.setText(text);
		if (hidden)
		{
			element.getPropertiesMap().setProperty(MetasJRXlsExporter.PROPERTY_COLUMN_HIDDEN, "true");
		}
		return element;
	}

	private static HSSFSheet exportAndReadFirstSheet(final JasperPrint print) throws Exception
	{
		final byte[] xls = JasperEngine.exportAsExcel(print).getReportContent();
		try (final HSSFWorkbook workbook = new HSSFWorkbook(new ByteArrayInputStream(xls)))
		{
			return workbook.getSheetAt(0);
		}
	}

	private static short passwordHash(final String password)
	{
		return (short)CryptoFunctions.createXorVerifier1(password);
	}

	@Test
	void noPasswordProperty_sheetIsProtectedWithDefaultPassword() throws Exception
	{
		final HSSFSheet sheet = exportAndReadFirstSheet(buildTestPrint(null, false));

		assertThat(sheet.getProtect()).isTrue();
		assertThat(sheet.getPassword()).isEqualTo(passwordHash(DEFAULT_PASSWORD));
	}

	@Test
	void customPasswordProperty_sheetIsProtectedWithThatPassword() throws Exception
	{
		final HSSFSheet sheet = exportAndReadFirstSheet(buildTestPrint("secret", false));

		assertThat(sheet.getProtect()).isTrue();
		assertThat(sheet.getPassword()).isEqualTo(passwordHash("secret"));
	}

	@Test
	void emptyPasswordProperty_sheetIsNotProtected() throws Exception
	{
		final HSSFSheet sheet = exportAndReadFirstSheet(buildTestPrint("", false));

		assertThat(sheet.getProtect()).isFalse();
		assertThat(sheet.getPassword()).isZero();
	}

	@Test
	void columnHiddenProperty_hidesThatColumn() throws Exception
	{
		final HSSFSheet sheet = exportAndReadFirstSheet(buildTestPrint(null, true));

		assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("visible");
		assertThat(sheet.isColumnHidden(0)).isFalse();
		assertThat(sheet.isColumnHidden(1)).isTrue();
	}

	@Test
	void withoutColumnHiddenProperty_noColumnIsHidden() throws Exception
	{
		final HSSFSheet sheet = exportAndReadFirstSheet(buildTestPrint(null, false));

		assertThat(sheet.isColumnHidden(0)).isFalse();
		assertThat(sheet.isColumnHidden(1)).isFalse();
	}
}
