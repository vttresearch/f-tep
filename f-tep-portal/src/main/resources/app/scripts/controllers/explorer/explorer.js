/**
 * @ngdoc function
 * @name ftepApp.controller:ExplorerCtrl
 * @description
 * # ExplorerCtrl
 * Controller of the ftepApp
 */
'use strict';
define(['../../ftepmodules'], function (ftepmodules) {

    ftepmodules.controller('ExplorerCtrl', ['$scope', '$rootScope', '$mdDialog', 'TabService', 'MessageService', 'ftepProperties', 'CommonService', 'CommunityService', 'MapService', '$timeout', function ($scope, $rootScope, $mdDialog, TabService, MessageService, ftepProperties, CommonService, CommunityService, MapService, $timeout) {

        /* Set active page */
        $scope.navInfo = TabService.navInfo.explorer;
        $scope.bottomNavTabs = TabService.getBottomNavTabs();

        /* Active session message count */
        $scope.message = {};
        $scope.message.count = MessageService.countMessages();
        $scope.$on('update.messages', function(event) {
            $scope.message.count = MessageService.countMessages();
        });

        /* SIDE BAR */
        $scope.sideNavTabs = TabService.getExplorerSideNavs();

        function showSidebarArea() {
            $scope.navInfo.sideViewVisible = true;
        }

        $scope.hideSidebarArea = function () {
            $scope.navInfo.activeSideNav = undefined;
            $scope.navInfo.sideViewVisible = false;
        };

        $scope.toggleSidebar = function (tab) {
            if($scope.navInfo.activeSideNav === tab) {
                $scope.hideSidebarArea();
            } else {
                $scope.navInfo.activeSideNav = tab;
                showSidebarArea();
            }
        };

        $scope.$on('update.selectedService', function(event) {
            $scope.navInfo.activeSideNav = $scope.sideNavTabs.WORKSPACE;
            showSidebarArea();
        });
        /* END OF SIDE BAR */

        /** BOTTOM BAR **/
        // When a new Job has been started flip a flag
        var newjobWithBottombar = false;
        $scope.$on('newjob.started.init', function() {
            newjobWithBottombar = true;
        });

        $scope.displayTab = function(tab, allowedToClose) {
            if (newjobWithBottombar || allowedToClose === undefined) {
                $scope.toggleBottomView(true);
                // Transition without animation
                $scope.navInfo.activeBottomNav = tab;
            } else {
                if ($scope.navInfo.activeBottomNav === tab && allowedToClose !== false) {
                    // No need to switch tab
                    $scope.toggleBottomView();
                } else {
                    $scope.toggleBottomView(true);
                    // Transition with animation
                    $timeout(function () {
                        $scope.navInfo.activeBottomNav = tab;
                    }, 100);
                }
            }
        };

        // Triggering scroll action on the Jobs List when the document element is visible
        $scope.$on('newjob.started.show', function() {
            if (newjobWithBottombar) {
                // Timered watcher
                var checkExist = setInterval(function() {
                    // Scroll to first job item
                    document.querySelector("#jobs .job-list-item:nth-child(1)").scrollIntoView(true);
                    if ($('#jobs-container').length) {
                        // Resetting flag
                        newjobWithBottombar = false;
                        // Remove timer
                        clearInterval(checkExist);
                    }
                }, 100);
            }
        });

        $scope.toggleBottomView = function (flag) {
            if (flag === undefined) {
                $scope.navInfo.bottomViewVisible = !$scope.navInfo.bottomViewVisible;
            } else {
                $scope.navInfo.bottomViewVisible = flag;
            }
        };

        $scope.getOpenedBottombar = function(){
            if($scope.navInfo.bottomViewVisible){
                return $scope.navInfo.activeBottomNav;
            }
            else {
                return undefined;
            }
        };
        /** END OF BOTTOM BAR **/

        /** Set available SLD styles **/
        $scope.slds = MapService.defaultSlds;
        let slds = localStorage.getItem('slds');
        if (slds) {
            $scope.customSlds = JSON.parse(slds);
        }
        console.log($scope.customSlds);

        $scope.$on('slds.updated', function (event, slds) {
            $scope.slds = slds;
            console.log(slds);
        });

        $scope.sldMenuShowing = function(item) {
            if (item.show_sld_menu) {
              return true;
            }
            return false;
        };
        $scope.toggleSldMenu = function(item) {
            if (item.show_sld_menu) {
              delete item.show_sld_menu;
            } else {
              item.show_sld_menu = 1;
            }
        };
        $scope.hideSldMenu = function(item) {
            delete item.show_sld_menu;
        };

        $scope.toggleSldView = function(item) {
            $scope.navInfo.sldViewVisible = !$scope.navInfo.sldViewVisible;
            $scope.navInfo.sldViewItem = item.properties;
            $scope.sldColormap = JSON.parse(JSON.stringify(item.properties.sld.colormap));
        };
        $scope.hideSldView = function() {
            $scope.navInfo.sldViewVisible = false;
            $scope.navInfo.sldViewItem = undefined;
            $scope.sldColormap = undefined;
        };

        $scope.newSldRowBelow = function($event) {
            let index = $event.currentTarget.parentNode.rowIndex;
            $scope.sldColormap.splice(index, 0, { 'quantity':0,'color':'#FFFFFF' });
        }
        $scope.newSldRowAbove = function($event) {
            let index = $event.currentTarget.parentNode.rowIndex - 1;
            $scope.sldColormap.splice(index, 0, { 'quantity':0,'color':'#FFFFFF' });
        }

        getColormap = function() {
            let table = document.getElementById('sldTable');
            let quantityInputs = table.getElementsByClassName('sldQuantityInput');
            let colorInputs = table.getElementsByClassName('sldColorInput');
            let cm = [];
            for (let i=0; i<quantityInputs.length; i++) {
                cm.push({ 'quantity':quantityInputs[i].value, 'color':colorInputs[i].value });
            }
            return cm;
        }

        $scope.applySld = function() {
            let cm = getColormap();
            $scope.navInfo.sldViewItem.sld = { 'colormap': cm };
            $scope.sldColormap = JSON.parse(JSON.stringify(cm));

            $rootScope.$broadcast('update.wmslayer', $scope.visibleWmsList);
        };

        $scope.saveSld = function() {
            let cm = getColormap();
            let sld = { 'name': 'test', 'colormap':cm };
            let customSlds = localStorage.getItem('slds');
            if (customSlds) { 
                customSlds = JSON.parse(customSlds);
            } else {
                customSlds = {};
            }
            customSlds[sld.name] = sld;
            localStorage.setItem('slds', JSON.stringify(customSlds));
        }

        /** WMS layer show/hide option for Product Search result items **/
        $scope.visibleWmsList = [];

        /* Toggles display of a wms item */
        $scope.toggleSearchResWMS = function ($event, item, show, sld) {
            if (show) {
                item.properties.sld = sld;
                $scope.visibleWmsList.push(item.properties);
            } else {
                var index = $scope.visibleWmsList.indexOf(item.properties);
                $scope.visibleWmsList.splice(index, 1);
            }
            $rootScope.$broadcast('update.wmslayer', $scope.visibleWmsList);
        };

        /* Clear visible WMS-s when map is reset */
        $scope.$on('map.cleared', function () {
            $scope.visibleWmsList = [];
        });

        $scope.hasWmsLink = function(item) {
            return item.properties._links.wms;
        };

        $scope.isSearchResWmsVisible = function(item) {
            return $scope.visibleWmsList.indexOf(item.properties) > -1;
        };
        /** END OF WMS layer show/hide option for Product Search result items **/

        /* Show Result Metadata Modal */
        $scope.showMetadata = function($event, data) {
            function MetadataController($scope, $mdDialog, ftepProperties) {
                $scope.item = data;

                $scope.closeDialog = function() {
                    $mdDialog.hide();
                };

                $scope.estimateAndDownload = function() {
                    CommonService.estimateDownloadCost($event, $scope.item.properties._links.download.href);
                };
            }
            MetadataController.$inject = ['$scope', '$mdDialog', 'ftepProperties'];

            $mdDialog.show({
                controller: MetadataController,
                templateUrl: 'views/explorer/templates/metadata.tmpl.html',
                parent: angular.element(document.body),
                targetEvent: $event,
                clickOutsideToClose: true,
                locals: {
                    items: $scope.items
                }
            });
        };

        /* Share Object Modal */
        $scope.sharedObject = {};
        $scope.shareObjectDialog = function($event, item, type, serviceName, serviceMethod) {
            CommunityService.getObjectGroups(item, type).then(function(groups){
                CommonService.shareObjectDialog($event, item, type, groups, serviceName, serviceMethod, 'explorer');
            });
        };

        $scope.hideContent = true;
        var map, sidenav, navbar;
        $scope.finishLoading = function(component) {
            switch(component) {
                case 'map':
                    map = true;
                    break;
                case 'sidenav':
                    sidenav = true;
                    break;
                case 'navbar':
                    navbar = true;
                    break;
            }

            if (map && sidenav && navbar) {
                $scope.hideContent = false;
            }
        };

    }]);
});
