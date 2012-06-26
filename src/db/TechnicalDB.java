package db;

import java.io.InputStreamReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.Change;
import models.CommitFamily;
import models.Diff;
import db.util.ISetter;
import db.util.ISetter.StringSetter;
import db.util.PreparedStatementExecutionItem;
import fixinducingchanges.FixResources;

public class TechnicalDB extends DbConnection
{
	public TechnicalDB()
	{
		super();
	}
	
	public List<Diff> getDiffsFromCommitAndFile(String commitID, String file) {
		try {
			List<Diff> diffs = new ArrayList<Diff>();
			String sql = "SELECT * FROM file_diffs WHERE file_id=? AND new_commit_id=?";
			String[] parms = {file, commitID};
			ResultSet rs = execPreparedQuery(sql, parms);
			while(rs.next())
			{
				diffs.add(new Diff(file, commitID, rs.getString("old_commit_id"),
						rs.getString("diff_text"), rs.getInt("char_start"), rs.getInt("char_end"), 
						rs.getString("diff_type")));
			}
			
			return diffs;
		}
		catch(SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Get onwer breakdown for a file for a specific commit.
	 * If the current commit does not have the file, look for 
	 * its parent commit until find one.
	 * @param FileId
	 * @param CommitId
	 * @return
	 */
	public List<Change> getAllOwnersForFileAtCommit(String FileId, String CommitId, List<CommitFamily> commitPath)
	{
		// get all the onwer entries for this file since this commit
		Map<String, List<Change>> commitMap = getAllFileOwnerChangesBefore(FileId, CommitId);
		
		// traverse comithPath if grab the first found as it has the latest owner break down.
		for(CommitFamily cf: commitPath)
		{
			String commitId = cf.getChildId();
			if(commitMap.containsKey(commitId))
				return commitMap.get(commitId);
		}

		return null;
	}
	
	public Map<String, List<Change>> getAllFileOwnerChangesBefore(String FileId, String CommitId)
	{
		try 
		{
			String sql = "SELECT commit_id, source_commit_id, file_id, owner_id, char_start, char_end, change_type FROM owners natural join commits where commit_date <= (select commit_date from commits where commit_id=?)" +
					"and (branch_id is NULL OR branch_id=?) and file_id=? order by commit_id;"; 
			String[] parms = {CommitId, branchID, FileId};
			ResultSet rs = execPreparedQuery(sql, parms);
			
			// Create a map for <Commit_id, List<Change>>
			Map<String, List<Change>> commitMap = new HashMap<String, List<Change>>();
			LinkedList<Change> currentChanges = new LinkedList<Change>();
			String currentCommit = "";
			
			while(rs.next())
			{
				String commitId = rs.getString("commit_id");
				if(currentCommit.isEmpty())
				{
					// first commit
					currentCommit = rs.getString("commit_id");
					currentChanges.add(new Change(rs.getString("owner_id"),
												  rs.getString("source_commit_id"), 
												  Resources.ChangeType.valueOf(rs.getString("change_type")),
												  rs.getString("file_id"),
												  rs.getInt("char_start"),
												  rs.getInt("char_end")));
					
				}
				else 
				{
					if(commitId.equals(currentCommit))
					{
						// same commit, push into current map
						currentChanges.add(new Change(rs.getString("owner_id"),
													  rs.getString("source_commit_id"), 
													  Resources.ChangeType.valueOf(rs.getString("change_type")),
													  rs.getString("file_id"),
													  rs.getInt("char_start"),
													  rs.getInt("char_end")));
					}
					else
					{
						// add new File map
						commitMap.put(currentCommit, currentChanges);
						currentCommit = commitId;
						currentChanges = new LinkedList<Change>();
						currentChanges.add(new Change(rs.getString("owner_id"),
													  rs.getString("source_commit_id"), 
													  Resources.ChangeType.valueOf(rs.getString("change_type")),
													  rs.getString("file_id"),
													  rs.getInt("char_start"),
													  rs.getInt("char_end")));
					}
				}
			}
			
			//Add last commit
			if(!currentCommit.isEmpty())
				commitMap.put(currentCommit, currentChanges);
			
			return commitMap;
		}
		catch(SQLException e) 
		{
			e.printStackTrace();
			return null;
		}
	}
	
	public Set<String> getChangedFilesForCommit(String CommitId)
	{
		try {
			Set<String> files = new HashSet<String>();
			String sql = "Select distinct file_id from file_diffs where new_commit_id=?";
			ISetter[] parms = {new StringSetter(1, CommitId)};
			PreparedStatementExecutionItem ei = new PreparedStatementExecutionItem(sql, parms);
			this.addExecutionItem(ei);
			ei.waitUntilExecuted();
			while (ei.getResult().next())
			{
				files.add(ei.getResult().getString("file_id"));
			}
			return files;
		}
		catch(SQLException e)
		{
			e.printStackTrace();
			return null;
		}
	}
	
	public void exportBugs(Set<String> bugs, String fix) {
		for(String bug: bugs) {
			String query = "INSERT INTO fix_inducing (bug, fix) VALUES " +
					"(?, ?)";
			ISetter[] params = {
					new StringSetter(1,bug),
					new StringSetter(2,fix)
			};
			PreparedStatementExecutionItem ei = new PreparedStatementExecutionItem(query, params);
			addExecutionItem(ei);
			ei.waitUntilExecuted();
		}
	}
	
	public void createTable() {
		try {
			// Drop the table if it already exists
			String query = "DROP TABLE IF EXISTS fix_inducing";
			PreparedStatementExecutionItem ei = new PreparedStatementExecutionItem(query, null);
			addExecutionItem(ei);
			ei.waitUntilExecuted();

			runScript(new InputStreamReader(FixResources.class.getResourceAsStream("createTable.sql")));
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
