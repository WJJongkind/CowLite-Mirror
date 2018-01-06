package filedatareader;

import java.util.ArrayList;
import java.util.List;

/**
 * (C) This class contains all of the matches in a file searched through with a FileDataReader
 * @author Wessel Jongkind
 */
public class MatchSet
{
    //fields
    private ArrayList<List<String>> matches;
    
    public MatchSet()
    {
        matches = new ArrayList<>();
    }
    
    /**
     * Add a match to the set. Should be an ArrayList with each index containing a group
     * @param theMatch the match to be added
     */
    public void addMatch(List<String> theMatch)
    {
        matches.add(theMatch);
    }
    
    /**
     * Returns a match including all of it's groups. Each group is in a different index of the ArrayList
     * @param match the match to be returned.
     * @return the match with all of it's groups.
     */
    public List<String> getMatch(int match)
    {
        return matches.get(match);
    }
    
    /**
     * Returns the specified group of the specified match
     * @param match the match containing the group
     * @param group the group to be returned
     * @return the contents of the group
     */
    public String getMatchGroup(int match, int group)
    {
        return matches.get(match).get(group);
    }
    
    /**
     * Calculates the amount of groups that a match has
     * @return groupcount per match
     */
    public int getMatchGroupCount()
    {
        return matches.get(0).size();
    }
    
    /**
     * Calculates the  amount of matches found
     * @return the amount of matches
     */
    public int getMatchCount()
    {
        return matches.size();
    }
    
    /**
     * Returns the total amount of groups with of all matches combined
     * @return total amount of groups
     */
    public int getTotalGroupCount()
    {
        return matches.size() * getMatchGroupCount();
    }
}
