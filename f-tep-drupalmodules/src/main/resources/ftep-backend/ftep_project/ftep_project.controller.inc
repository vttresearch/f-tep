<?php
class FtepProjectsController extends EntityAPIController { }
class FtepProjectUIController extends EntityDefaultUIController {
    /**
     * Overrides hook_menu() defaults.
     */
    public function hook_menu() {
        $items = parent::hook_menu ();
        $items [$this->path] ['description'] = 'Manage Projects';
        return $items;
    }
}
