package com.converter.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfReaderService {

    public static List<String> readPdfLines(String pdfFilePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (PDDocument document = PDDocument.load(new File(pdfFilePath))) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(document);

            String[] splitLines = text.split("\\r?\\n");
            for (String line : splitLines) {
                lines.add(line.trim());
            }
        }
        return lines;
    }
}