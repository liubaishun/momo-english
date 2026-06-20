package com.momo.repository;

import com.momo.model.WordBombSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WordBombSnapshotRepository extends JpaRepository<WordBombSnapshot, String> {
    // 继承原生标准的 findById, save, deleteById 即可满足战术原子锁需求
}