/**
 * @ngdoc function
 * @name ftepApp.controller:SldCtrl
 * @description
 * # SldCtrl
 * Controller of the ftepApp
 */
'use strict';
define(['../../../ftepmodules'], function (ftepmodules) {

    ftepmodules.controller('SldCtrl', ['$scope', '$rootScope', 'MapService', 'TabService', function ($scope, $rootScope, MapService, TabService) {

        $scope.navInfo = TabService.navInfo.explorer;

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
            $scope.editorSld.colormap.splice(index, 1);
        }
		$scope.addColormapTemplate = function() {
            $scope.editorSld.colormap.push({ 'quantity':0,'color':'#FFFFFF' });
            $scope.editorSld.colormap.push({ 'quantity':255,'color':'#000000' });
		}

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
			let dropdown = document.getElementById('sldmenu');
            $scope.editorSld = JSON.parse(JSON.stringify($scope.slds[dropdown.selectedIndex]));
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
						
						// Update SLD list
						$scope.slds = MapService.getSlds();
					}
				}
			}
		}


    }]);
});
