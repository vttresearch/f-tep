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
		$scope.defaultSld = MapService.defaultSld;

        $scope.$on('slds.updated', function (event, slds) {
            $scope.slds = slds;
            console.log($scope.slds);
        });

        $scope.openSldView = function(item) {
            $scope.navInfo.sldViewItem = item.properties;
            $scope.editorSld = JSON.parse(JSON.stringify(item.properties.sld));
			if (item.properties.sld.singleband) {
			  $scope.showSingleBand();
			} else {
			  $scope.showMultiBand();
			}
			$scope.editorSldName = item.properties.sld.name;
            $scope.navInfo.sldViewVisible = true;
        };
        $scope.hideSldView = function() {
            $scope.navInfo.sldViewVisible = false;
            $scope.navInfo.sldViewItem = undefined;
            $scope.editorSld = undefined;
        };
        $scope.showSingleBand = function() {
			$scope.editorSld.singleband = true;
        };
        $scope.showMultiBand = function() {
			$scope.editorSld.singleband = false;
        };

        $scope.newSldRowBelow = function($event) {
            let index = $event.currentTarget.parentNode.rowIndex;
            $scope.editorSld.colormap.splice(index, 0, { 'quantity':0,'color':'#FFFFFF' });
        }
        $scope.newSldRowAbove = function($event) {
            let index = $event.currentTarget.parentNode.rowIndex - 1;
            $scope.editorSld.colormap.splice(index, 0, { 'quantity':0,'color':'#FFFFFF' });
        }
        $scope.deleteSldRow = function($event) {
            let index = $event.currentTarget.parentNode.rowIndex - 1; // -1 because of header row
			//console.log(index);
            $scope.editorSld.colormap.splice(index, 1);
			//console.log($scope.editorSld);
        }
		$scope.addColormapTemplate = function() {
            $scope.editorSld.colormap.push({ 'quantity':0,'color':'#FFFFFF' });
            $scope.editorSld.colormap.push({ 'quantity':255,'color':'#000000' });
		}

        /** Parse SLD from the SLD view values **/
        parseSld = function() {
			let sld = { 'name': '', channelselection:{} };
			if ($scope.editorSld.singleband) {
				sld.singleband = true;
				let grayBand = document.getElementById('sldgrayband').value;
				let grayMin = document.getElementById('sldgraybandmin').value;
				let grayMax = document.getElementById('sldgraybandmax').value;
				if (grayBand != '') {
					sld.channelselection.gray = parseInt(grayBand);
					if (grayMin != '') {
						sld.channelselection.graymin = parseInt(grayMin);
					}
					if (grayMax != '') {
						sld.channelselection.graymax = parseInt(grayMax);
					}
				}
			
				let table = document.getElementById('sldTable');
				let quantityInputs = table.getElementsByClassName('sldQuantityInput');
				let colorInputs = table.getElementsByClassName('sldColorInput');
				let cm = [];
				for (let i=0; i<quantityInputs.length; i++) {
					cm.push({ 'quantity':quantityInputs[i].value, 'color':colorInputs[i].value });
				}
				sld.colormap = cm;
			} else {
				sld.singleband = false;
				let redBand = document.getElementById('sldredband').value;
				let redMin = document.getElementById('sldredbandmin').value;
				let redMax = document.getElementById('sldredbandmax').value;
				if (redBand != '') {
					sld.channelselection.red = parseInt(redBand);
					if (redMin != '') {
						sld.channelselection.redmin = parseInt(redMin);
					}
					if (redMax != '') {
						sld.channelselection.redmax = parseInt(redMax);
					}
				}
				let greenBand = document.getElementById('sldgreenband').value;
				let greenMin = document.getElementById('sldgreenbandmin').value;
				let greenMax = document.getElementById('sldgreenbandmax').value;
				if (greenBand != '') {
					sld.channelselection.green = parseInt(greenBand);
					if (greenMin != '') {
						sld.channelselection.greenmin = parseInt(greenMin);
					}
					if (greenMax != '') {
						sld.channelselection.greenmax = parseInt(greenMax);
					}
				}
				let blueBand = document.getElementById('sldblueband').value;
				let blueMin = document.getElementById('sldbluebandmin').value;
				let blueMax = document.getElementById('sldbluebandmax').value;
				if (blueBand != '') {
					sld.channelselection.blue = parseInt(blueBand);
					if (blueMin != '') {
						sld.channelselection.bluemin = parseInt(blueMin);
					}
					if (blueMax != '') {
						sld.channelselection.bluemax = parseInt(blueMax);
					}
				}
			}
			
            return sld;
        }

		// Populate the style editor view with stored values
        $scope.sldSelectionChanged = function() {
			//console.log($scope.editorSld);
			let dropdown = document.getElementById('sldmenu');
			//console.log(dropdown.selectedIndex);
			//console.log($scope.slds[dropdown.selectedIndex]);
            //$scope.navInfo.sldViewItem.sld = JSON.parse(JSON.stringify($scope.slds[dropdown.selectedIndex]));
            $scope.editorSld = JSON.parse(JSON.stringify($scope.slds[dropdown.selectedIndex]));
			$scope.editorSldName = $scope.editorSld.name;
		}

        $scope.applySld = function() {
            let sld = parseSld();
            $scope.navInfo.sldViewItem.sld = sld;
            $scope.editorSld = JSON.parse(JSON.stringify(sld));
			$scope.editorSldName = '';

            $rootScope.$broadcast('update.wmslayer', $scope.visibleWmsList);
        };

        $scope.saveSld = function() {
			let name = window.prompt("Please give name for the saved custom style. Using an existing name overwrites the old style.", "");
			//console.log(name);
			if (name == null) {
				return;
			}
			if (name == $scope.defaultSld.name) {
				window.alert('Default style cannot be overdriven, please select another name!');
			} else if (name == '') {
				window.alert('Name cannot be empty!');
			} else {
				let sld = parseSld();
				let customSlds = localStorage.getItem('slds');
				if (customSlds) { 
					customSlds = JSON.parse(customSlds);
				} else {
					customSlds = {};
				}
				sld.name = name;
				customSlds[sld.name] = sld;
				localStorage.setItem('slds', JSON.stringify(customSlds));
				$scope.slds = MapService.getSlds();
				$scope.editorSldName = name; // To select the saved SLD from dropdown 
			}
        }
		$scope.deleteSld = function() {
			if ($scope.editorSld) {
				if ($scope.editorSld.builtin) {
					window.alert('Built-in styles cannot be deleted');
				} else {
					let customSlds = localStorage.getItem('slds');
					if (customSlds) { 
						customSlds = JSON.parse(customSlds);
						delete customSlds[$scope.editorSld.name];
						localStorage.setItem('slds', JSON.stringify(customSlds));
						$scope.slds = MapService.getSlds();
					}
				}
			}
		}

        /** WMS layer show/hide option for Product Search result items **/
        $scope.visibleWmsList = [];

        /* Toggles display of a wms item */
        $scope.toggleSearchResWMS = function ($event, item, show, sld) {
            if (show) {
                if (sld == undefined && item.properties.sld == undefined) {
                    sld = JSON.parse(JSON.stringify($scope.defaultSld));
                }
				if (sld) {
                    item.properties.sld = sld;
				}
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
