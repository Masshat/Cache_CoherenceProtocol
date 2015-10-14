package model;

import java.util.ArrayList;
import java.util.List;

import utils.Utile;

/**
 * Class modeling a request. It contains data and metadata: cycles at the which the request was sent and received
 * @author QLM
 *
 */
public class Request {

	/**
	 * All the possible request and response types. It should not be necessary to add other types,
	 * and all of them should be used either in the write-through or write-back model.
	 */
	public enum cmd_t {
		NOP,
		READ_WORD,
		RSP_READ_WORD,
		READ_LINE,
		RSP_READ_LINE,
		RSP_READ_LINE_EX,
		WRITE_LINE,
		RSP_WRITE_LINE,
		WRITE_WORD,
		RSP_WRITE_WORD,
		GETM,
		RSP_GETM,
		GETM_LINE,
		RSP_GETM_LINE,
		INVAL,
		RSP_INVAL_CLEAN,
		RSP_INVAL_DIRTY,
		INVAL_RO,
		RSP_INVAL_RO_CLEAN,
		RSP_INVAL_RO_DIRTY,
		UPDATE,
		RSP_UPDATE,
	}

	private long r_address;
	private int r_srcid;
	private int r_tgtid;
	private cmd_t r_cmd;
	// written data if write request
	// unused if read request
	// read data if read response
	// unsused if write response
	List<Long> r_data;
	private int r_be;

	private int r_cycle;
	private int r_start_cycle;
	private int r_end_cycle;
	
	boolean r_start_cycle_set = false;
	boolean addedToFinishedReqs = false;

	public Request() {
		r_address = 0x0;
		r_srcid = 0;
		r_tgtid = 0;
		r_cmd = cmd_t.NOP;
		r_be = 0;
		r_cycle = 0;
		r_start_cycle = 0;
		r_end_cycle = 0;
		r_data = null;
	}

	public Request(long address, int srcid, int tgtid, cmd_t cmd, int start_cycle, int max_duration, List<Long> data, int be) {
		r_address = address;
		r_srcid = srcid;
		r_tgtid = tgtid;
		r_cmd = cmd;
		r_be = be;
		r_cycle = start_cycle;
		this.r_start_cycle = start_cycle;
		initData(data);
		
		r_end_cycle = start_cycle + Utile.randInt(0, max_duration);
	}

	public Request(long address, int srcid, int tgtid, cmd_t cmd, int start_cycle, int max_duration) {
		r_address = address;
		r_srcid = srcid;
		r_tgtid = tgtid;
		r_cmd = cmd;
		r_be = 0xF;
		r_cycle = start_cycle;
		r_start_cycle = start_cycle;
		initData(null);

		r_end_cycle = start_cycle + Utile.randInt(0, max_duration);
	}

	private void initData(List<Long> data) {
		if (data != null) {
			r_data = new ArrayList<Long>();

			for (int i = 0; i < data.size(); i++) {
				r_data.add(data.get(i));
			}
		}
		else {
			r_data = null;
		}
	}

	public long getAddress() {
		return r_address;
	}

	public int getSrcid() {
		return r_srcid;
	}

	public int getTgtid() {
		return r_tgtid;
	}

	public cmd_t getCmd() {
		return r_cmd;
	}

	public int getBe() {
		return r_be;
	}

	public int getNwords() {
		if (r_data == null) {
			return 0;
		}
		else {
			return r_data.size();
		}
	}

	List<Long> getData() {
		return r_data;
	}
	
	public void updateTgtid(int id) {
		assert(r_tgtid == -1);
		r_tgtid = id;
	}

	void simulate1Cycle() {
		r_cycle++;
	}
	
	public int getStartCycle() {
		return r_start_cycle;
	}

	public int getEndCycle() {
		return r_end_cycle;
	}
	
	public void setStartCycle(int cycle) {
		// no assert because this function can be called twice
		if (!r_start_cycle_set) {
			r_start_cycle = cycle;
			r_start_cycle_set = true;
		}
	}
	
	public void addToFinishedReqs(int cycle) {
		assert(!addedToFinishedReqs);
		r_end_cycle = cycle;
		addedToFinishedReqs = true;
	}
	
	boolean toPop() {
		return (r_cycle >= r_end_cycle);
	}

	public String toString() {
		String res;
		res = "--- Request ---\n";
		res += "    address: 0x" + Long.toHexString(r_address) + "\n";
		res += "    srcid: " + r_srcid + "\n";
		res += "    tgtid: " + r_tgtid + "\n";
		res += "    cmd: " + r_cmd + "\n";
		if (r_data != null) {
			res += "    ndata_words: " + r_data.size() + "\n";
			res += "    data: [ " + r_data.get(0);
			for (int i = 1; i < r_data.size(); i++) {
				res += ", " + r_data.get(i);
			}
			res += " ]\n";
		}
		else {
			res += "    ndata_words: 0\n";
		}
		res += "--- End Request ---\n";
		return res;
	}
	
	public String toStringBis() {
		String res;
		res = "{ @ 0x" + Long.toHexString(r_address);
		res += " / srcid: " + r_srcid;
		res += " / tgtid: " + r_tgtid;
		res += " / " + r_cmd + "\n";
		if (r_data != null) {
			res += " / ndata_words: " + r_data.size();
			res += " / data: [ " + r_data.get(0);
			for (int i = 1; i < r_data.size(); i++) {
				res += ", " + r_data.get(i);
			}
			res += " ] }";
		}
		else {
			res += " / ndata_words: 0 }";
		}
		return res;
	}

}
