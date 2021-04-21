package chasm;

import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;

/*
**  This class will merge individual edits into a single larger edit.
**  That is, characters entered sequentially will be grouped together and
**  undone as a group. Any attribute changes will be considered as part
**  of the group and will therefore be undone when the group is undone.
*/
public class CompoundUndoManager extends UndoManager
	implements UndoableEditListener, DocumentListener {
    private UndoManager undoManager;
    private CompoundEdit compoundEdit;
    private JTextComponent textComponent;
    private UndoAction undoAction;
    private RedoAction redoAction;

    //  These fields are used to help determine whether the edit is an
    //  incremental edit. The offset and length should increase by 1 for
    //  each character added or decrease by 1 for each character removed.

    private int lastOffset;
    private int lastLength;
    private boolean newCompoundFlag;

    public CompoundUndoManager(JTextComponent textComponent) {
        this.textComponent = textComponent;
        undoManager = this;
        undoAction = new UndoAction();
        redoAction = new RedoAction();
        textComponent.getDocument().addUndoableEditListener(this);
    }

    /*
    **  Add a DocumentLister before the undo is done so we can position
    **  the Caret correctly as each edit is undone.
    */
    @Override
    public void undo() {
        textComponent.getDocument().addDocumentListener(this);
        super.undo();
        textComponent.getDocument().removeDocumentListener(this);
    }

    /*
    **  Add a DocumentLister before the redo is done so we can position
    **  the Caret correctly as each edit is redone.
    */
    @Override
    public void redo() {
        textComponent.getDocument().addDocumentListener(this);
        super.redo();
        textComponent.getDocument().removeDocumentListener(this);
    }
    
    public void compoundEditShouldEnd() {
        newCompoundFlag = true;
    }

    /*
    **  Whenever an UndoableEdit happens the edit will either be absorbed
    **  by the current compound edit or a new compound edit will be started
    */
    @Override
    public void undoableEditHappened(UndoableEditEvent e) {
        int offsetChange = textComponent.getCaretPosition() - lastOffset;
        int lengthChange = textComponent.getDocument().getLength() - lastLength;
        
        //  Check for an attribute change and kill it
        AbstractDocument.DefaultDocumentEvent event = (AbstractDocument.DefaultDocumentEvent) e.getEdit();
        
        if(event.getType().equals(DocumentEvent.EventType.CHANGE)) {
            e.getEdit().die();
            return;
        }
        
        if(compoundEdit == null || newCompoundFlag) {
            compoundEdit = startCompoundEdit(e.getEdit());
            return;
        }
        
        if(offsetChange == lengthChange && Math.abs(offsetChange) == 1) {
            compoundEdit.addEdit(e.getEdit());
            lastOffset = textComponent.getCaretPosition();
            lastLength = textComponent.getDocument().getLength();
        } else {
            compoundEdit = startCompoundEdit(e.getEdit());
        }
    }

    /*
    **  Each CompoundEdit will store a group of related incremental edits
    **  (ie. each character typed or backspaced is an incremental edit)
    */
    private CompoundEdit startCompoundEdit(UndoableEdit anEdit) {
        if(compoundEdit != null)
            compoundEdit.end();
        
        newCompoundFlag = false;
        
        lastOffset = textComponent.getCaretPosition();
        lastLength = textComponent.getDocument().getLength();

        //  The compound edit is used to store incremental edits
        compoundEdit = new MyCompoundEdit();
        compoundEdit.addEdit(anEdit);
        
        //  The compound edit is added to the UndoManager. All incremental
        //  edits stored in the compound edit will be undone/redone at once
        addEdit(compoundEdit);
        
        undoAction.updateUndoState();
        redoAction.updateRedoState();

        return compoundEdit;
    }

    /*
     *  The Action to Undo changes to the Document.
     *  The state of the Action is managed by the CompoundUndoManager
     */
    public Action getUndoAction() {
        return undoAction;
    }

    /*
     *  The Action to Redo changes to the Document.
     *  The state of the Action is managed by the CompoundUndoManager
     */
    public Action getRedoAction() {
        return redoAction;
    }
//
//  Implement DocumentListener
//
    /*
     *  Updates to the Document as a result of Undo/Redo will cause the
     *  Caret to be repositioned
     */
    @Override
    public void insertUpdate(final DocumentEvent e) {
        SwingUtilities.invokeLater(() -> {
            int offset = e.getOffset() + e.getLength();
            offset = Math.min(offset, textComponent.getDocument().getLength());
            textComponent.setCaretPosition(offset);
        });
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        textComponent.setCaretPosition(e.getOffset());
    }

    @Override
    public void changedUpdate(DocumentEvent e) {}

    class MyCompoundEdit extends CompoundEdit {
        @Override
        public boolean isInProgress() {
            return false;
        }

        @Override
        public void undo() throws CannotUndoException {
            //  End the edit so future edits don't get absorbed by this edit
            if(compoundEdit != null)
                compoundEdit.end();

            super.undo();

            //  Always start a new compound edit after an undo
            compoundEdit = null;
        }
    }

    /* Perform the Undo and update the state of the undo/redo Actions */
    class UndoAction extends AbstractAction {
        public UndoAction() {
            putValue(Action.NAME, "Undo");
            putValue(Action.SHORT_DESCRIPTION, getValue(Action.NAME));
            putValue(Action.MNEMONIC_KEY, KeyEvent.VK_U);
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke("control Z"));
            setEnabled(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                undoManager.undo();
                textComponent.requestFocusInWindow();
            } catch (CannotUndoException ex) {}

            updateUndoState();
            redoAction.updateRedoState();
        }

        private void updateUndoState() {
            setEnabled(undoManager.canUndo());
        }
    }

    /*
     *	Perform the Redo and update the state of the undo/redo Actions
     */
    class RedoAction extends AbstractAction {
        public RedoAction() {
            putValue(Action.NAME, "Redo");
            putValue(Action.SHORT_DESCRIPTION, getValue(Action.NAME));
            putValue(Action.MNEMONIC_KEY, KeyEvent.VK_R);
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK));
            setEnabled(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                undoManager.redo();
                textComponent.requestFocusInWindow();
            } catch (CannotRedoException ex) {}

            updateRedoState();
            undoAction.updateUndoState();
        }

        protected void updateRedoState() {
            setEnabled(undoManager.canRedo());
        }
    }
}