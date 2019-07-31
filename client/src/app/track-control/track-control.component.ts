import {ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges} from '@angular/core';
import {Track} from '../model/track.model';
import {TrackState} from '../model/track-state.enum';

@Component({
    selector: 'app-track-control',
    templateUrl: './track-control.component.html',
    styleUrls: ['./track-control.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class TrackControlComponent implements OnInit, OnChanges {

    @Input()
    track: Track;

    @Input()
    volume: number;

    @Output()
    skip: EventEmitter<void> = new EventEmitter();

    @Output()
    togglePlay: EventEmitter<void> = new EventEmitter();

    @Output()
    volumeChange: EventEmitter<number> = new EventEmitter();

    togglePlayIcon: string;

    constructor() {
    }

    ngOnInit() {
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.track) {
            switch (this.track.state) {
                case TrackState.Playing:
                    this.togglePlayIcon = 'pause';
                    break;

                case TrackState.Ready:
                    this.togglePlayIcon = 'play_arrow';
                    break;

                default:
                    break;
            }
        }
    }

}
