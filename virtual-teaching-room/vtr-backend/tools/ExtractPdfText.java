import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExtractPdfText {
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        try (PDDocument document = Loader.loadPDF(input.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            Files.writeString(output, stripper.getText(document));
            System.out.println("pages=" + document.getNumberOfPages() + ", output=" + output);
        }
    }
}
