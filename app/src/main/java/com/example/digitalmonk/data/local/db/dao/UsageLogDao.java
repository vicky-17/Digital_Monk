package com.example.digitalmonk.data.local.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.digitalmonk.data.local.db.entity.UsageLogEntity;
import java.util.List;

@Dao
public interface UsageLogDao {
    @Insert
    void insert(UsageLogEntity log);

    @Query("SELECT * FROM usage_logs ORDER BY timestamp DESC")
    List<UsageLogEntity> getAllLogs();

    @Query("DELETE FROM usage_logs")
    void deleteAll();
}