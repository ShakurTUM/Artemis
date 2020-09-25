import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';

import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { TextBlockType } from 'app/entities/text-block.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Result } from 'app/entities/result.model';
import { Course } from 'app/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { TextAssessmentsService } from 'app/exercises/text/assess/text-assessments.service';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { JhiAlertService } from 'ng-jhipster';
import { TranslateService } from '@ngx-translate/core';
import { Feedback } from 'app/entities/feedback.model';

@Component({
    template: '',
})
export abstract class TextAssessmentBaseComponent implements OnInit {
    exercise: TextExercise | null;
    isAtLeastInstructor: boolean;
    protected userId: number | null;

    protected get course(): Course | undefined {
        return this.exercise?.course || this.exercise?.exerciseGroup?.exam?.course;
    }

    protected constructor(
        protected jhiAlertService: JhiAlertService,
        protected accountService: AccountService,
        protected assessmentsService: TextAssessmentsService,
        translateService: TranslateService,
        protected structuredGradingCriterionService: StructuredGradingCriterionService,
    ) {}

    async ngOnInit() {
        // Used to check if the assessor is the current user
        const identity = await this.accountService.identity();
        this.userId = identity ? identity.id : null;
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
    }

    protected computeTotalScore(assessments: Feedback[]): number {
        return this.structuredGradingCriterionService.computeTotalScore(assessments);
    }

    protected handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        this.jhiAlertService.success(translationKey);
    }

    protected handleError(error: HttpErrorResponse): void {
        const errorMessage = error.headers?.get('X-artemisApp-message') || error.message;
        this.jhiAlertService.error(errorMessage, null, undefined);
    }

    /**
     * Sorts text block refs by there appearance and checks for overlaps or gaps.
     * Prevent duplicate text when manual and automatic text blocks are present.
     *
     * @param matchBlocksWithFeedbacks
     * @param textBlockRefs
     * @param unusedTextBlockRefs
     * @param submission
     */
    protected sortAndSetTextBlockRefs(
        matchBlocksWithFeedbacks: TextBlockRef[],
        textBlockRefs: TextBlockRef[],
        unusedTextBlockRefs: TextBlockRef[],
        submission: TextSubmission | null,
    ) {
        // Sort by start index to process all refs in order
        const sortedRefs = matchBlocksWithFeedbacks.sort((a, b) => a.block.startIndex - b.block.startIndex);

        let previousIndex = 0;
        const lastIndex = submission?.text?.length || 0;
        for (let i = 0; i <= sortedRefs.length; i++) {
            let ref: TextBlockRef | undefined = sortedRefs[i];
            const nextIndex = ref ? ref.block.startIndex : lastIndex;

            // last iteration, nextIndex = lastIndex. PreviousIndex > lastIndex is a sign for illegal state.
            if (!ref && previousIndex > nextIndex) {
                console.error('Illegal State: previous index cannot be greated than the last index!');

                // new text block starts before previous one ended (overlap)
            } else if (previousIndex > nextIndex) {
                const previousRef = textBlockRefs.pop();
                if (!previousRef) {
                    console.error('Overlapping Text Blocks with nothing?', previousRef, ref);
                } else if ([ref, previousRef].every((r) => r.block.type === TextBlockType.AUTOMATIC)) {
                    console.error('Overlapping AUTOMATIC Text Blocks!', previousRef, ref);
                } else if ([ref, previousRef].every((r) => r.block.type === TextBlockType.MANUAL)) {
                    console.error('Overlapping MANUAL Text Blocks!', previousRef, ref);
                } else {
                    // Find which block is Manual and only keep that one. Automatic block is stored in `unusedTextBlockRefs` in case we need to restore.
                    switch (TextBlockType.MANUAL) {
                        case previousRef.block.type:
                            unusedTextBlockRefs.push(ref);
                            ref = previousRef;
                            break;
                        case ref.block.type:
                            unusedTextBlockRefs.push(previousRef);
                            this.addTextBlockByIndices(previousRef.block.startIndex, nextIndex, submission!, textBlockRefs);
                            break;
                    }
                }

                // If there is a gap between the current and previous block (most likely whitespace or linebreak), we need to create a new text block as well.
            } else if (previousIndex < nextIndex) {
                // There is a gap. We need to add a Text Block in between
                this.addTextBlockByIndices(previousIndex, nextIndex, submission!, textBlockRefs);
                previousIndex = nextIndex;
            }

            if (ref) {
                textBlockRefs.push(ref);
                previousIndex = ref.block.endIndex;
            }
        }
    }

    private addTextBlockByIndices(startIndex: number, endIndex: number, submission: TextSubmission, textBlockRefs: TextBlockRef[]): void {
        if (startIndex >= endIndex) {
            return;
        }

        const newRef = TextBlockRef.new();
        newRef.block.startIndex = startIndex;
        newRef.block.endIndex = endIndex;
        newRef.block.setTextFromSubmission(submission!);
        newRef.block.computeId();
        textBlockRefs.push(newRef);
    }
}
