package com.taobao.tddl.executor.cursor.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.taobao.tddl.executor.common.CursorMetaImp;
import com.taobao.tddl.executor.common.ICursorMeta;
import com.taobao.tddl.executor.cursor.ISchematicCursor;
import com.taobao.tddl.executor.cursor.SchematicCursor;
import com.taobao.tddl.executor.rowset.IRowSet;
import com.taobao.tddl.executor.utils.ExecUtils;
import com.taobao.tddl.optimizer.config.table.ColumnMeta;
import com.taobao.tddl.optimizer.core.expression.IOrderBy;
import com.taobao.tddl.optimizer.core.expression.ISelectable;
import com.taobao.tddl.optimizer.core.plan.query.IJoin;

/**
 * join的结果
 * 
 * @author mengshi.sunmengshi 2013-12-3 上午10:56:08
 * @since 5.1.0
 */
public class JoinSchematicCursor extends SchematicCursor {

    protected ISchematicCursor    left_cursor;
    protected ISchematicCursor    right_cursor;
    /**
     * 因为左右cursor要拼到一起，所以右值必须加一个偏移量
     */
    protected Integer             rightCursorOffset;
    private boolean               schemaInited   = false;
    protected ICursorMeta         joinCursorMeta = null;
    protected Comparator<IRowSet> kvPairComparator;
    protected List<ISelectable>   leftJoinOnColumns;
    protected List<ISelectable>   rightJoinOnColumns;
    protected List<ISelectable>   leftColumns;
    protected List<ISelectable>   rightColumns;

    /**
     * 见 com.taobao.ustore.optimizer.node.lazy.query.JoinNode
     * leftOuterJoin:leftOuter=true && rightOuter=false
     * rightOuterJoin:leftOuter=false && rightOuter=true
     * innerJoin:leftOuter=true && rightOuter=true outerJoin:leftOuter=false &&
     * rightOuter=false
     */
    protected boolean             leftOutJoin    = true;
    protected boolean             rightOutJoin   = true;

    public JoinSchematicCursor(ISchematicCursor left_cursor, ISchematicCursor right_cursor, List leftJoinOnColumns,
                               List rightJoinOnColumns, List leftColumns, List rightColumns){
        super(null, null, null);
        this.left_cursor = left_cursor;
        this.right_cursor = right_cursor;

        this.leftJoinOnColumns = leftJoinOnColumns;
        this.rightJoinOnColumns = rightJoinOnColumns;
        this.leftColumns = leftColumns;
        this.rightColumns = rightColumns;
        schemaInited = false;
    }

    // protected void buildSchemaInJoin(ISchematicCursor left_cursor,
    // ISchematicCursor right_cursor) {
    // ICursorMeta leftCursorMeta = left_cursor.getMeta();
    // ICursorMeta rightCursorMeta = right_cursor.getMeta();
    //
    // buildSchemaInJoin(leftCursorMeta, rightCursorMeta);
    //
    // }

    protected void setLeftRightJoin(IJoin join) {
        if (join != null) {
            this.leftOutJoin = join.getLeftOuter();
            this.rightOutJoin = join.getRightOuter();
        } else {
            throw new RuntimeException("IJoin join is null");
        }
    }

    public boolean isLeftOutJoin() {
        return leftOutJoin;
    }

    public boolean isRightOutJoin() {
        return rightOutJoin;
    }

    protected void buildSchemaInJoin(ICursorMeta leftCursorMeta, ICursorMeta rightCursorMeta) {
        if (schemaInited) {
            return;
        }

        schemaInited = true;
        // 以左面数据顺序，作为排序
        setOrderBy(left_cursor);

        List<ColumnMeta> leftColumns = leftCursorMeta.getColumns();
        List<ColumnMeta> rightColumns = rightCursorMeta.getColumns();
        this.kvPairComparator = ExecUtils.getComp(this.leftJoinOnColumns,
            this.rightJoinOnColumns,
            leftCursorMeta,
            rightCursorMeta);
        List<ColumnMeta> newJoinColumnMsg = new ArrayList<ColumnMeta>(leftColumns.size() + rightColumns.size());
        rightCursorOffset = leftCursorMeta.getIndexRange();
        newJoinColumnMsg.addAll(leftColumns);
        newJoinColumnMsg.addAll(rightColumns);
        List<Integer> indexes = new ArrayList<Integer>(newJoinColumnMsg.size());
        addIndexToNewIndexes(leftCursorMeta, leftColumns, indexes, 0);
        addIndexToNewIndexes(rightCursorMeta, rightColumns, indexes, rightCursorOffset);
        ICursorMeta cursorMetaImpJoin = CursorMetaImp.buildNew(newJoinColumnMsg,
            indexes,
            (leftCursorMeta.getIndexRange() + rightCursorMeta.getIndexRange()));
        // setMeta(cursorMetaImpJoin);

        this.joinCursorMeta = cursorMetaImpJoin;

    }

    protected void buildSchemaFromReturnColumns(List<ColumnMeta> leftColumns, List<ColumnMeta> rightColumns) {
        if (schemaInited) {
            return;
        }

        schemaInited = true;
        // 以左面数据顺序，作为排序
        setOrderBy(left_cursor);

        // List<ColumnMeta> leftColumns = leftCursorMeta.getColumns();
        // List<ColumnMeta> rightColumns = rightCursorMeta.getColumns();
        // this.kvPairComparator = ExecUtil.getComp(this.leftJoinOnColumns,
        // this.rightJoinOnColumns, leftCursorMeta,
        // rightCursorMeta);
        List<ColumnMeta> newJoinColumnMsg = new ArrayList<ColumnMeta>(leftColumns.size() + rightColumns.size());
        // rightCursorOffset = leftCursorMeta.getIndexRange();
        newJoinColumnMsg.addAll(leftColumns);
        newJoinColumnMsg.addAll(rightColumns);
        // List<Integer> indexes = new
        // ArrayList<Integer>(newJoinColumnMsg.size());
        // addIndexToNewIndexes(leftCursorMeta, leftColumns, indexes, 0);
        // addIndexToNewIndexes(rightCursorMeta, rightColumns, indexes,
        // rightCursorOffset);
        ICursorMeta cursorMetaImpJoin = CursorMetaImp.buildNew(newJoinColumnMsg);
        // setMeta(cursorMetaImpJoin);
        rightCursorOffset = leftColumns.size();
        this.joinCursorMeta = cursorMetaImpJoin;

    }

    private void addIndexToNewIndexes(ICursorMeta cursorMeta, List<ColumnMeta> columns, List<Integer> indexes,
                                      int offset) {
        for (ColumnMeta cm : columns) {
            Integer index = cursorMeta.getIndex(cm.getTableName(), cm.getName());
            if (index == null) index = cursorMeta.getIndex(cm.getTableName(), cm.getAlias());
            indexes.add(offset + index);
        }
    }

    private void setOrderBy(ISchematicCursor left_cursor) {
        List<IOrderBy> orderBys = left_cursor.getOrderBy();
        setOrderBy(orderBys);
    }

    @Override
    public List<Exception> close(List<Exception> exs) {
        if (left_cursor != null) {
            exs = left_cursor.close(exs);
        }
        if (right_cursor != null) {
            exs = right_cursor.close(exs);
        }

        return exs;
    }
}
