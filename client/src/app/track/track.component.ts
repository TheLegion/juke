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
import {TrackState} from '../model/track-state.enum';

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

    canAddTrack: boolean;

    @HostBinding('class')
    private sourceTypeClass: string;

    constructor() {
    }

    ngOnInit() {
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.track) {
          if (this.track) {
            const fomrCache = this.track.source === TrackSource.Cache;
            const isDownloading = this.track.state === TrackState.Downloading;
            this.sourceTypeClass = fomrCache ? 'cache' : isDownloading ? 'download' : 'vk';
          } else {
            this.sourceTypeClass = null;
          }
        }
        if (this.track) {
            this.canAddTrack = this.canAdd && !this.track['__added'];
        } else {
            this.canAddTrack = this.canAdd;
        }
    }

    addTrack() {
        this.add.emit();
        this.track['__added'] = true;
        this.canAddTrack = this.canAdd && !this.track['__added'];
    }
}
