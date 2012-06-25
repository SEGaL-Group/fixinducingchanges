package db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import models.Change;
import models.Commit;


public class FixInducingDB extends DbConnection
{
	public FixInducingDB()
	{
		super();
	}
	
	/**
	 * This functions returns all commits that are in between two commit IDs 
	 * inclusively.
	 * @param commitID_start
	 * @param commitID_end
	 * @return
	 */
	public List<Commit> getAllCommits(String commitID_start, String commitID_end) {
		try {
			List<Commit> commits = new ArrayList<Commit>();
			String sql = "SELECT id, commit_id, author, author_email, comments, commit_date FROM commits WHERE " +
					"commit_date <= (select commit_date from commits where commit_id=?) AND " +
					"commit_date >= (select commit_date from commits where commit_id=?) AND " +
					"(branch_id is NULL or branch_id=?) " +
					"ORDER BY commit_date, commit_id";
			String[] params = {commitID_end, commitID_start, branchID};
			ResultSet rs = execPreparedQuery(sql, params);
			while(rs.next())
				commits.add(new Commit(rs.getInt("id"), rs.getString("commit_id"), rs.getString("author"),
						rs.getString("author_email"), rs.getString("comments"), null, branchID));
			
			return commits;
		}
		catch(SQLException e) {
			return null;
		}
	}
	
	public List<Change> getAllOwnersForFileAtCommit(String FileId, String CommitId)
	{
		try 
		{
			LinkedList<Change> changes = new LinkedList<Change>();
			String sql = "SELECT source_commit_id, file_id, owner_id, char_start, char_end, change_type FROM owners natural join commits where commit_id=?" +
					"and (branch_id is NULL OR branch_id=?) and file_id=? order by commit_date, commit_id, char_start;"; 
			String[] parms = {CommitId, branchID, FileId};
			ResultSet rs = execPreparedQuery(sql, parms);
			while(rs.next())
			{
				changes.add(new Change(rs.getString("owner_id"), rs.getString("source_commit_id"), 
						Resources.ChangeType.valueOf(rs.getString("change_type")), rs.getString("file_id"),
						rs.getInt("char_start"), rs.getInt("char_end")));
			}
			return changes;
		}
		catch(SQLException e) 
		{
			e.printStackTrace();
			return null;
		}
	}
}