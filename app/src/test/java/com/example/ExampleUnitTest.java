package com.example;

import org.junit.Test;
import static org.junit.Assert.*;

import com.example.model.PdfFile;

public class ExampleUnitTest {

    @Test
    public void testPdfFileSizingFormat() {
        PdfFile pdf = new PdfFile("/storage/emulated/0/Download/test.pdf", "test.pdf", 2048, System.currentTimeMillis());
        
        // 2048 bytes should format to "2.0 KB"
        assertEquals("2.0 KB", pdf.getFormattedSize());
        
        pdf.setSize(1048576); // 1 MB
        assertEquals("1.0 MB", pdf.getFormattedSize());
    }

    @Test
    public void testPdfFileNaming() {
        PdfFile pdf = new PdfFile("/storage/emulated/0/Download/test.pdf", "test.pdf", 1000, System.currentTimeMillis());
        assertEquals("test.pdf", pdf.getName());
        
        pdf.setName("new_name.pdf");
        assertEquals("new_name.pdf", pdf.getName());
    }
}
