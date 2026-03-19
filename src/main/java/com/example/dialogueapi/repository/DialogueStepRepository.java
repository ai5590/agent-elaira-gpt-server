package com.example.dialogueapi.repository;

import com.example.dialogueapi.entity.DialogueStepEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DialogueStepRepository extends JpaRepository<DialogueStepEntity, Long> {

    Optional<DialogueStepEntity> findByDialogIdAndStepId(String dialogId, int stepId);
}
