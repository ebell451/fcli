/*
 * Copyright 2021-2025 Open Text.
 *
 * The only warranties for products and services of Open Text
 * and its affiliates and licensors ("Open Text") are as may
 * be set forth in the express warranty statements accompanying
 * such products and services. Nothing herein should be construed
 * as constituting an additional warranty. Open Text shall not be
 * liable for technical or editorial errors or omissions contained
 * herein. The information contained herein is subject to change
 * without notice.
 */
package com.fortify.cli.common.output.writer.record.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fortify.cli.common.json.JsonHelper;
import com.fortify.cli.common.output.writer.record.RecordWriterConfig;
import com.fortify.cli.common.output.writer.record.impl.RecordWriterTable.TableWriter;
import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RecordWriterTable extends AbstractRecordWriter<TableWriter> {
    @Getter private final RecordWriterConfig config;
    private static final int BATCH_SIZE = 100; // Unified batch size for sampling & segment output
    
    @Override
    protected void append(TableWriter out, ObjectNode formattedRecord) throws IOException {
        out.append(formattedRecord);
    }
    
    @Override
    protected Function<ObjectNode, ObjectNode> createRecordFormatter(ObjectNode objectNode) throws IOException {
        // For tables, we always flatten, keeping the original dot-separated property path as headers
        return createStructuredOutputTransformer(true, Function.identity());
    }   
    
    @Override
    protected void close(TableWriter out) throws IOException {
        out.close();
    }
    
    @Override
    protected void closeWithNoData(Writer writer) throws IOException {
        writer.write("No data");
        writer.flush();
        writer.close();
    }
    
    @Override
    protected TableWriter createOut(Writer writer, ObjectNode formattedRecord) throws IOException {
        if ( formattedRecord==null ) { return null; }
        return new TableWriter(writer, formattedRecord.properties().stream().map(e->e.getKey()).toList());
    }
    
    @RequiredArgsConstructor
    protected final class TableWriter implements Closeable { 
        private final Writer writer;
        private final List<String> headers; 
        private final List<String[]> rows = new ArrayList<>();
        private boolean widthsCalculated = false;
        private int[] calculatedColumnWidths; // Final widths including padding (AsciiTable internal representation)
        // Note: total table width currently not tracked beyond column width calculation; add later if needed
        private long totalRowCount = 0; // For informational purposes (could be used later)
        private Character[] firstAndOnlySegmentBorders; // Original borders for single segment, or first segment prior to multi determination
        private Character[] firstOfMultiSegmentBorders; // First segment when multiple segments follow (Z123 replaced by OPQR)
        private Character[] intermediateSegmentBorders; // Intermediate segments (top & header removed, bottom uses OPQR)
        private Character[] lastSegmentBorders; // Final segment in multi (top & header removed, bottom uses original Z123)
        
        public void append(ObjectNode formattedRecord) {
            rows.add(asColumnArray(formattedRecord));
            totalRowCount++;
            if ( !widthsCalculated && rows.size()==BATCH_SIZE ) {
                calculateColumnWidths();
                outputSegment(false); // Output first segment without closing writer; may have more rows coming
                rows.clear();
            } else if ( widthsCalculated && rows.size()==BATCH_SIZE ) {
                outputSegment(false);
                rows.clear();
            }
        }
        
        private final String[] asColumnArray(ObjectNode formattedRecord) {
            return headers.stream().map(h->getColumnValue(formattedRecord, h)).toArray(String[]::new);
        }

        private final String getColumnValue(ObjectNode formattedRecord, String property) {
            var node = formattedRecord.get(property);
            if ( node==null || node.isNull() ) { return "N/A"; }
            if ( node.isArray() ) { 
                // TODO Did we handle this in TableRecordWriter in the old framework, or
                //      was this handled somewhere else? 
                return JsonHelper.stream((ArrayNode)node)
                        .map(n->n.asText())
                        .collect(Collectors.joining(","));
            }
            return node.asText();
        }

        @Override
        public void close() throws IOException {
            if ( !widthsCalculated ) {
                // Handle case where we have fewer than BATCH_SIZE rows; calculate widths now
                if ( !rows.isEmpty() ) {
                    calculateColumnWidths();
                }
            }
            if ( rows.isEmpty() && totalRowCount==0 ) {
                writer.write("No data\n");
            } else if ( !rows.isEmpty() ) {
                // Output last (or only) segment, marking as final
                outputSegment(true);
            } else {
                // Previous segments have already been written, need to append bottom border of final segment
                // Only applicable if we segmented before and didn't yet write final border in last outputSegment call
                // The logic inside outputSegment(true) writes bottom border, so if rows is empty we must have written final border already.
            }
            writer.flush();
            writer.close();
        }
        
        private final String asTable(Character[] borders, boolean includeHeaders) {
            if ( rows.isEmpty() ) { return ""; }
            Column[] columns = headers.stream()
                .map(h->{
                    var col = new Column()
                        .dataAlign(HorizontalAlign.LEFT)
                        .headerAlign(HorizontalAlign.LEFT);
                    if ( includeHeaders && config.getStyle().withHeaders() ) {
                        col.header(formatHeader(h));
                    }
                    if ( calculatedColumnWidths!=null ) {
                        int idx = headers.indexOf(h);
                        int width = calculatedColumnWidths[idx];
                        // AsciiTable internally adds 2*PADDING (2) to computed content length; we pre-calculated widths
                        // as desired content width; add padding now so resulting width matches expectation.
                        int paddedWidth = width + 2; 
                        col.minWidth(paddedWidth).maxWidth(paddedWidth, config.getStyle().isWrap() ? OverflowBehaviour.NEWLINE : OverflowBehaviour.ELLIPSIS_RIGHT);
                    }
                    return col;
                })
                .toArray(Column[]::new);
            var result = AsciiTable.getTable(borders, columns, rows.toArray(String[][]::new));
            if ( config.getStyle().isMarkdownBorder() ) {
                result = result.replaceAll("(?m)^\\s+$", "").replaceAll("(?m)^\\n", ""); 
            }
            return result;
        }

        private void outputSegment(boolean finalSegment) {
            var borders = determineSegmentBorders(finalSegment);
            var table = asTable(borders, shouldIncludeHeadersForCurrentSegment());
            if ( table.isEmpty() ) { return; }
            try {
                writer.write(table);
                writer.write('\n');
                writer.flush();
            } catch ( IOException e ) {
                throw new RuntimeException("Error writing table segment", e);
            }
        }

        private boolean shouldIncludeHeadersForCurrentSegment() {
            // Include headers only for first segment (when widthsCalculated just became true)
            // widthsCalculated is set in calculateColumnWidths(), before first segment is written
            return totalRowCount <= BATCH_SIZE; // If we've only processed the first batch
        }

        private Character[] determineSegmentBorders(boolean finalSegment) {
            // Lazily prepare border variants.
            // Mapping indexes for clarity (0..28): A B C D E F G H I J K L M N O P Q R S T U V W X Y Z 1 2 3
            // Requirements recap:
            // - If only one segment: original borders unchanged
            // - For all segments after first: null A B C D (0..3) & H I J K (7..10)
            // - Intermediate segments: Z123 (25..28) replaced by OPQR (14..17)
            // - Last segment: keep Z123 original
            // - First segment when there WILL be more segments: should also have OPQR instead of Z123 (to avoid premature final bottom border)
            if ( firstAndOnlySegmentBorders==null ) {
                firstAndOnlySegmentBorders = getBorders();
                firstOfMultiSegmentBorders = createFirstMultiSegmentBorders(firstAndOnlySegmentBorders);
                intermediateSegmentBorders = createContinuationBorders(firstAndOnlySegmentBorders); // Intermediate
                lastSegmentBorders = createFinalMultiSegmentBorders(firstAndOnlySegmentBorders); // Final multi-segment
            }
            if ( totalRowCount <= BATCH_SIZE ) {
                // We are writing the first segment. We don't yet know for sure (at call time) if more rows will come
                // but logic in append() writes first segment only when rows.size()==BATCH_SIZE meaning there ARE more rows (or could be).
                // Distinguish by finalSegment flag: if finalSegment==true we know no further rows -> single segment.
                return finalSegment ? firstAndOnlySegmentBorders : firstOfMultiSegmentBorders;
            }
            // Subsequent segments
            return finalSegment ? lastSegmentBorders : intermediateSegmentBorders;
        }
        private Character[] createFirstMultiSegmentBorders(Character[] original) {
            Character[] modified = original.clone();
            // Replace Z123 (25..28) with OPQR (14..17) while retaining top/header separators.
            for ( int src=14, dst=25; src<=17; src++, dst++ ) { modified[dst]=original[src]; }
            return modified;
        }

        private Character[] createContinuationBorders(Character[] original) {
            Character[] modified = original.clone();
            // Null top border & header separator entries: ABCD (0..3) and HIJK (7..10)
            for ( int i=0;i<=3;i++ ) { modified[i]=null; }
            for ( int i=7;i<=10;i++ ) { modified[i]=null; }
            // Replace Z123 (25..28) with OPQR (14..17) for intermediate segments
            for ( int src=14, dst=25; src<=17; src++, dst++ ) { modified[dst]=original[src]; }
            return modified;
        }

        private Character[] createFinalMultiSegmentBorders(Character[] original) {
            Character[] modified = original.clone();
            // Null top border & header separator entries: ABCD (0..3) and HIJK (7..10)
            for ( int i=0;i<=3;i++ ) { modified[i]=null; }
            for ( int i=7;i<=10;i++ ) { modified[i]=null; }
            // Keep original Z123 (25..28) for final segment
            return modified;
        }

        private void calculateColumnWidths() {
            widthsCalculated = true;
            // Measure raw max content widths from sample
            int[] dataWidths = new int[headers.size()];
            for ( var row : rows ) {
                for ( int i=0;i<row.length;i++ ) {
                    int w = maxLineLength(row[i]);
                    if ( w>dataWidths[i] ) { dataWidths[i]=w; }
                }
            }
            int[] headerWidths = headers.stream().mapToInt(h->maxLineLength(formatHeader(h))).toArray();
            int[] minWidths = new int[headers.size()];
            int[] maxWidths = new int[headers.size()];
            for ( int i=0;i<headers.size();i++ ) {
                int headerWidth = headerWidths[i];
                minWidths[i] = Math.max(6, headerWidth); // Minimal width rules
                maxWidths[i] = Math.max(minWidths[i], dataWidths[i]);
            }
            int terminalWidth = com.fortify.cli.common.util.ConsoleHelper.getTerminalWidthOrDefault();
            // Calculate overhead: border & padding; approximate as per AsciiTable algorithm
            int paddingPerCol = 2; // AsciiTable adds 2*PADDING=2 spaces
            int columnSeparators = headers.size()-1; // '|' or separator characters between columns
            int verticalBorders = 2; // left & right borders (if present)
            var borderChars = getBorders();
            boolean hasOuterBorder = borderChars[0]!=null && borderChars[3]!=null; // Use presence of top corners as proxy
            if ( !hasOuterBorder ) { verticalBorders = 0; }
            int overhead = paddingPerCol*headers.size() + columnSeparators + verticalBorders; 
            int availableForContent = terminalWidth - overhead;
            // First assign min widths
            int[] finalWidths = Arrays.copyOf(minWidths, minWidths.length);
            int remaining = availableForContent - Arrays.stream(finalWidths).sum();
            if ( remaining<0 ) { // Terminal too small; shrink columns proportionally (keeping >=6)
                // Simple fallback: keep min widths; data will overflow & be truncated/wrapped by AsciiTable
                remaining = 0; 
            }
            // Distribute remaining width up to maxWidths evenly
            while ( remaining>0 ) {
                boolean progress=false;
                for ( int i=0;i<finalWidths.length && remaining>0;i++ ) {
                    if ( finalWidths[i]<maxWidths[i] ) {
                        finalWidths[i]++;
                        remaining--; progress=true;
                    }
                }
                if ( !progress ) { break; }
            }
            // Store widths including padding; AsciiTable expects width including padding
            calculatedColumnWidths = finalWidths; // Content widths (excluding padding)
        }

        private int maxLineLength(String text) {
            if ( text==null ) { return 0; }
            int max=0; int start=0; int len=text.length();
            for ( int i=0;i<=len;i++ ) {
                if ( i==len || text.charAt(i)=='\n' ) {
                    int lineLen = i-start; if ( lineLen>max ) { max=lineLen; }
                    start=i+1;
                }
            }
            return max;
        }

        private String formatHeader(String header) {
            return header.startsWith("_.") ? "" : header;
        }

        private Character[] getBorders() {
            var style = config.getStyle();
            if ( style.isMarkdownBorder() ) {
                return "    ||||-|||||               ".chars().mapToObj(c -> (char)c).toArray(Character[]::new);
            } else if ( style.isBorder() ) {
                return AsciiTable.BASIC_ASCII;
            } else {
                return AsciiTable.NO_BORDERS;
            }
        }
    }
}
