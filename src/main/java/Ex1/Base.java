package Ex1;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class Base {

    private static String dir = "files" + File.separator;

    protected static class Operation {
        String fileName;
        private String operation;
        private String input;
        private String output;

        protected Operation(String line) {
            String[] sp = line.split("\t");
            this.operation = sp[0];
            this.input = sp[1];
            sp = input.split("/");
            this.fileName = sp[sp.length - 1];
            this.output = null;
        }

        protected String getOperation(){ return operation; }
        protected String getInput(){ return input; }
        protected String getOutput(){ return output; }
        protected String getFileName() { return fileName; }

        protected void setOutput(String output){
            if(this.output == null)
                this.output = output;
            else
                System.out.printf("cant redefine output of file: %s\n", this.input);
        }
        @Override
        public String toString(){
            return "<" + this.operation + ">: " + this.input + " " + this.output;
        }
    }

    public static void main(String[] args) {
        final String USAGE =
                "To run this,supply an input file output file and number of files per worker\n";
        if (args.length < 3) {
            System.out.println(USAGE);
            System.exit(1);
        }
        String input = args[0];
        String output = args[1];
        int n = Integer.parseInt(args[2]);
        String term = null;
        if(args.length == 4) {
            term = args[3];
        }
        System.out.printf("need %d workers\n", n);
        List<Operation> op = readfile(input);
        if(op.size() == 0){
            System.out.println("There was an error reading the file\n");
            System.exit(1);
        }
        File file = new File("files");
        file.mkdir();
        work(op);
        makeHTML(op,output);
        if(term != null){
            System.out.println("TERMINATE!!!");
        }
    }

    private static List<Operation> readfile(String input) {
        List<Operation> opList = new ArrayList<>();
        try {
            File file = new File(input);
            FileReader fr = new FileReader(file);
            BufferedReader br = new BufferedReader(fr);
            String line;
            while((line = br.readLine()) != null) {
                Operation op = new Operation(line);
                opList.add(op);
            }
            fr.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return opList;
    }

    private static void work(List<Operation> Op){
        int n = Op.size();
        int i = 1;
        for(Operation op : Op){
            try {
                System.out.printf("started download : %d/%d, file name: %s\n", i++, n, op.getFileName());
                URL url = new URL(op.getInput());
                HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                InputStream is=connection.getInputStream();
                PDDocument document = PDDocument.load(is);
                System.out.println("finished download, starting to remove pages");
                removePages(document);
                System.out.println("finished removing pages, starting to convert");
                File newFile = makeOperation(op, document);
                System.out.println("DONE!!!");
                document.close();
                op.setOutput(newFile.getAbsolutePath());
            }
            catch (IOException e){
                System.out.println("Something went wrong, check output.html when finished for more info");
                op.setOutput("<" + e.toString() + ">");
            }
        }
    }

    private static void removePages(PDDocument document){
        int noOfPages= document.getNumberOfPages();
        for(int i = 1; i < noOfPages; i++)
            document.removePage(1);
    }

    private static File makeOperation(Operation op, PDDocument document) throws IOException{
        File file;
        String name;
        switch (op.getOperation()){
            case "ToImage":
                PDFRenderer renderer = new PDFRenderer(document);
                BufferedImage image = renderer.renderImage(0);
                name = op.getFileName().split("\\.")[0] + ".png";
                file = new File(dir + name);
                file.createNewFile();
                ImageIO.write(image, "png", file);
                break;
            case "ToHTML":
                PDFTextStripper pdfStripper = new PDFTextStripper();
                String text = pdfStripper.getText(document);
                name = op.getFileName().split("\\.")[0] + ".html";
                file = new File(dir + name);
                file.createNewFile();
                FileWriter myWriter = new FileWriter(file);
                myWriter.write(text);
                myWriter.close();
                break;
            case "ToText":
                pdfStripper = new PDFTextStripper();
                text = pdfStripper.getText(document);
                name = op.getFileName().split("\\.")[0] + ".txt";
                file = new File(dir + name);
                file.createNewFile();
                myWriter = new FileWriter(file);
                myWriter.write(text);
                myWriter.close();
                break;
            default:
                throw new IOException("Operation is Not one of the defaults");
        }
        return file;
    }

    public static void makeHTML(List<Operation> Op, String output){
        try {
            File file = new File(output + ".html");
            file.createNewFile();
            FileWriter myWriter = new FileWriter(file);
            for(Operation op: Op)
                myWriter.write(op.toString() + "\n");
            myWriter.close();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
}
