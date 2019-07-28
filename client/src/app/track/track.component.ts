import {
    ChangeDetectionStrategy,
    Component,
    EventEmitter,
    HostBinding,
    Input,
    OnChanges,
    OnInit,
    Output,
    SimpleChanges
} from '@angular/core';
import {Track} from '../model/track.model';
import {TrackSource} from '../model/track-source';

@Component({
    selector: 'app-track',
    templateUrl: './track.component.html',
    styleUrls: ['./track.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class TrackComponent implements OnInit, OnChanges {

    @Input()
    track: Track;

    @Input()
    canAdd = false;

    @Output()
    add: EventEmitter<void> = new EventEmitter();

    @HostBinding('class')
    private sourceTypeClass: string;


    constructor() {
    }

    ngOnInit() {
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.track) {
            if (this.track) {
                this.sourceTypeClass = this.track.source === TrackSource.Cache ? 'cache' : 'vk';
            } else {
                this.sourceTypeClass = null;
            }
        }
    }

    addTrack() {
        this.add.emit();
        this.canAdd = false;
    }
}
