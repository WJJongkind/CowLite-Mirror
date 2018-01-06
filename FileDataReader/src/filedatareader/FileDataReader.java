package filedatareader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * (C) This class reads a file if it exists and returns it's data in
 * different foms.
 * @author Wessel Jongkind
 */
public class FileDataReader
{
    //fields
    private String path;
    private ArrayList<String> data;
    
    public FileDataReader()
    {
        data = new ArrayList<>();
    }
    
    /**
     * Set the path to an existing file
     * @param path path to the file
     * @throws Exception File not found
     */
    public void setPath(String path) throws Exception
    {
        this.path = path;
        readData();
    }
    
    private void readData() throws Exception
    {
        FileReader fred = new FileReader(path);
        BufferedReader bufred = new BufferedReader(fred);
        String dataline;
        while((dataline = bufred.readLine()) != null)
            data.add(dataline);
        
        bufred.close();
        fred.close();
    }
    
    /**
     * Get the path of the currently read file
     * @return the filepath of the  current file
     */
    public String getPath()
    {
        return path;
    }
    
    /**
     * Get the path of the current file
     * @return The currently read file
     */
    public File getFile()
    {
        return new File(path);
    }
    
    /**
     * Get all of the data of a file in 1 string not split by \n
     * @return All the data in the file in a string
     */
    public String getDataString()
    {
        String allData = "";
        for(String datapart: data)
            allData = allData + datapart;
        
        return allData;
    }
    
    /**
     * Get all of the data of a file seperated by \n
     * @return String with all the data on different lines
     */
    public String getDataDividedString()
    {
        String allData = "";
        
        for(String datapart: data)
            allData = allData + datapart + "\n";
        
        return allData;
    }
    
    /**
     * Get all of the file's different lines as an ArrayList
     * @return ArrayList with Strings of datalines
     */
    public ArrayList<String> getDataStringLines()
    {
       return data; 
    }
    
    /**
     * Get all of the file's different lines as an ArrayList
     * @return ArrayList with Doubles of datalines
     * @throws Exception when file contains non numeric data
     */
    public ArrayList<Double> getNumericDataLines() throws Exception
    {
        ArrayList<Double> numeric = new ArrayList<>();
        
        try{
            for(String datapart: data)
                numeric.add(Double.parseDouble(datapart));
        }catch(Exception e){
            throw new Exception("File contains non-numeric data");
        }
        
        return numeric;
    }
    
    /**
     * Puts the bytes of a file in an ArrayList
     * @return the bytes of a file
     * @throws Exception 
     */
    public ArrayList<Byte> getDataBytes() throws Exception
    {
        ArrayList<Byte> bytes = new ArrayList<>();
        
        for(byte b: Files.readAllBytes(Paths.get(path)))
            bytes.add(b);
        
        return bytes;
    }
    
    /**
     * Gets the file name of the currently used file
     * @return file name
     */
    public String getFileName()
    {
        return new File(path).getName();
    }
    
    /**
     * Checks wether the file contains any matches at all
     * @param regex the regexcode to check the file with
     * @return wether a match hsa been found
     * @throws Exception 
     */
    public boolean containsMatch(String regex) throws Exception
    {
        try{
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher;
            for(int i = 0; i < data.size(); i++)
            {
                matcher = pattern.matcher(data.get(i));
                if(matcher.find())
                    return true;
            }
            return false;
        }catch(Exception e){throw new Exception("Regex is not valid");}
    }
    
    /**
     * Puts all matches and their groups in a MatchSet
     * @param regex The regex to be used for searching through the file
     * @return MatchSet containing all matches
     * @throws Exception 
     */
    public MatchSet getRegexMatches(String regex) throws Exception
    {
        try{
            //Prepare the  matcher and matchset
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher;
            MatchSet matches = new MatchSet();
            
            //Iterate over all lines of the file
            for(int i = 0; i < data.size(); i++)
            {
                matcher = pattern.matcher(data.get(i));
                int groupcount = matcher.groupCount();
                while(matcher.find())
                {
                    ArrayList<String> match = new ArrayList<>();
                    
                    //Adds all groups to a match
                    for(int k = 0; k < groupcount + 1; k++)
                        match.add(matcher.group(k));
                    
                    matches.addMatch(match);
                }
            }
            return matches;
        }catch(Exception e){throw new Exception("Regex is not valid");}
    }
}
